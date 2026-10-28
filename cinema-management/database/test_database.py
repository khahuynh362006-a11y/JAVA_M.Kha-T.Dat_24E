"""Import and verify on a NEW cinema_management_test_* database only.

Uses mysql.exe, no third-party Python packages. Never drops a database or resets
an existing schema. Credentials: interactive provisioning outside this runner,
or inherited MYSQL_PWD (never printed/saved). No plaintext application passwords.
"""
import argparse
import concurrent.futures
from datetime import datetime
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', required=True, help='Absolute path to mysql.exe')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', default=13316, type=int)
    parser.add_argument('--user', default='root')
    parser.add_argument('--database', default='cinema_management_test_' + datetime.now().strftime('%Y%m%d_%H%M%S'))
    args = parser.parse_args()
    if not re.fullmatch(r'cinema_management_test_[A-Za-z0-9_]+', args.database):
        parser.error('Only a NEW cinema_management_test_* database is allowed')
    cli = [args.mysql, '--no-defaults', '--protocol=TCP', '--host='+args.host,
           '--port='+str(args.port), '--user='+args.user, '--connect-timeout=5',
           '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names', '--unbuffered']

    def command(sql, db=True, expect=0):
        result = subprocess.run(cli + (['--database='+args.database] if db else []),
                                input=sql.encode('utf-8'), stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=30)
        out = result.stdout.decode('utf-8', errors='replace')
        err = result.stderr.decode('utf-8', errors='replace')
        if expect == 0 and result.returncode != 0:
            raise RuntimeError(err + out)
        if expect != 0 and (result.returncode == 0 or f'ERROR {expect} ' not in err):
            raise AssertionError(f'Expected MySQL {expect}: {err} {out}')
        return out

    print(command('SELECT VERSION(), @@port; SHOW DATABASES;', db=False), flush=True)
    # Deliberately no IF NOT EXISTS: an existing test DB must not be overwritten.
    command(f'CREATE DATABASE `{args.database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;', db=False)
    print('DATABASE_CREATED', args.database, flush=True)
    directory = Path(__file__).resolve().parent
    for name in ('schema.sql', 'seed.sql', 'verify.sql'):
        print('IMPORT', name, flush=True)
        print(command((directory/name).read_text(encoding='utf-8')), flush=True)

    # Concurrency fixtures remain in this disposable DB for inspection.
    command("""
      SET SESSION time_zone='+07:00';
      INSERT INTO users(id,username,email,display_name,status)
        VALUES(20001,'concurrency_disabled','concurrency@example.invalid','No login fixture','INACTIVE');
      INSERT INTO bookings(id,code,showtime_id,room_id,created_by,created_by_name_snapshot,
          movie_title_snapshot,room_code_snapshot,price_version_snapshot,total_amount,age_checked)
        VALUES(20001,'RACE-A',1,1,20001,'Fixture','Demo','P01',0,80000,1),
              (20002,'RACE-B',1,1,20001,'Fixture','Demo','P01',0,80000,1),
              (20003,'RACE-C',1,1,20001,'Fixture','Demo','P01',0,80000,1),
              (20004,'RACE-D',1,1,20001,'Fixture','Demo','P01',0,80000,1);
    """)

    def seat_insert(booking, seat):
        return f"""INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,
          seat_code_snapshot,seat_type_snapshot,unit_price)
          VALUES({booking},1,1,{seat},'A{seat}','STANDARD',80000);"""

    def race(label, holder, waiter, seat, commit_holder):
        # mysql streams a marker after obtaining its unique index lock.
        # The second connection starts only after that marker, no guessed timing.
        proc = subprocess.Popen(cli+['--database='+args.database], stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        sql = 'START TRANSACTION;' + seat_insert(holder, seat) + "SELECT 'LOCK_READY'; DO SLEEP(3);"
        sql += ('COMMIT;' if commit_holder else 'ROLLBACK;')
        proc.stdin.write(sql.encode('utf-8'))
        proc.stdin.close()

        def read_marker():
            return proc.stdout.readline().decode('utf-8').strip()

        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
                marker = pool.submit(read_marker).result(timeout=10)
            if marker != 'LOCK_READY':
                raise AssertionError('Holder failed to acquire lock: '+marker)
            start = time.monotonic()
            command('START TRANSACTION;'+seat_insert(waiter, seat)+'COMMIT;',
                    expect=1062 if commit_holder else 0)
            waited = time.monotonic()-start
            proc.wait(timeout=10)
            if proc.returncode != 0:
                raise AssertionError(proc.stderr.read().decode('utf-8', errors='replace'))
            if waited < 1:
                raise AssertionError('Contender did not actually wait on holder lock')
            expected_winner = holder if commit_holder else waiter
            winner = command(f'SELECT booking_id FROM booking_seats WHERE showtime_id=1 AND active_seat_id={seat};').strip()
            if winner != str(expected_winner):
                raise AssertionError('Wrong final owner: '+winner)
            print(f'{label}\tPASS\twaited={waited:.2f}s owner={winner}', flush=True)
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait(timeout=5)

    race('36 concurrent COMMIT => contender rejected 1062',20001,20002,2,True)
    race('37 concurrent ROLLBACK => contender succeeds',20003,20004,3,False)
    # A caller can hold an old consistent-read snapshot; it must still never
    # reactivate a cancelled detail after another transaction cancels its parent.
    # Monotonic child transition in trigger is independent of that old snapshot.
    command("""SET SESSION time_zone='+07:00'; UPDATE bookings SET status='CANCELLED',
      cancelled_at=NOW(6),cancelled_by=20001,cancel_reason='Race fixture cleanup' WHERE id=20001;""")
    command(seat_insert(20002,2))
    command("UPDATE booking_seats SET booking_status='CONFIRMED' WHERE booking_id=20001;",expect=1644)
    print('38 cancelled race winner can be rebooked, old row cannot reactivate\tPASS',flush=True)
    print(command("""SELECT booking_id,showtime_id,seat_id,booking_status,active_seat_id,unit_price
      FROM booking_seats ORDER BY id;
      SELECT COUNT(*) AS overlaps FROM showtimes a JOIN showtimes b
        ON a.id<b.id AND a.room_id=b.room_id AND a.status='SCHEDULED' AND b.status='SCHEDULED'
        AND a.starts_at<DATE_ADD(b.ends_at,INTERVAL 15 MINUTE)
        AND b.starts_at<DATE_ADD(a.ends_at,INTERVAL 15 MINUTE);"""),flush=True)
    print('ALL 38 CHECKS PASSED. Test database retained for inspection:',args.database,flush=True)


if __name__ == '__main__':
    main()
