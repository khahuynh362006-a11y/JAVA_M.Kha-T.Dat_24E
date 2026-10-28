"""Verify V001 on a NEW cinema_management_test_* DB containing legacy history.

Standard Python + mysql CLI only. Inherit MYSQL_PWD if necessary; never print it.
No DROP/reset or modification of an existing database.
"""
import argparse
from datetime import datetime
import json
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', required=True)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--database', default='cinema_management_test_booking_upgrade_' + datetime.now().strftime('%Y%m%d_%H%M%S'))
    args = parser.parse_args()
    if not re.fullmatch(r'cinema_management_test_[A-Za-z0-9_]+', args.database):
        parser.error('A NEW cinema_management_test_* database is required')
    cli = [args.mysql, '--no-defaults', '--protocol=TCP', '--host='+args.host,
           '--port='+str(args.port), '--user='+args.user, '--default-character-set=utf8mb4',
           '--batch', '--raw', '--skip-column-names']

    def sql(text, selected=True):
        result = subprocess.run(cli+(['--database='+args.database] if selected else []),
                                input=text.encode('utf-8'), capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout.decode('utf-8').strip()

    directory = Path(__file__).resolve().parent
    sql('CREATE DATABASE `'+args.database+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;', False)
    for filename in ['schema.sql', 'seed.sql']:
        sql((directory/filename).read_text(encoding='utf-8'))
    sql("""INSERT INTO users(id,username,email,display_name,status)
      VALUES(9001,'upgrade-fixture','upgrade@example.invalid','Legacy fixture','INACTIVE');
      INSERT INTO bookings(id,code,showtime_id,room_id,created_by,created_by_name_snapshot,
        movie_title_snapshot,room_code_snapshot,price_version_snapshot,total_amount,age_checked)
      VALUES(9001,'LEGACY-UPGRADE',1,1,9001,'Legacy','Demo','P01',0,80000,1);
      INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price)
      VALUES(9001,1,1,1,'A1','STANDARD',80000);""")
    sql((directory/'migrations/V001__booking_idempotency.sql').read_text(encoding='utf-8'))
    assert sql("SELECT COUNT(*) FROM bookings WHERE id=9001 AND code='LEGACY-UPGRADE' AND total_amount=80000 AND idempotency_key IS NULL AND request_hash IS NULL;") == '1'
    assert sql('SELECT COUNT(*) FROM booking_seats WHERE booking_id=9001 AND active_seat_id=1;') == '1'
    sql("UPDATE bookings SET status='CANCELLED',cancelled_at=NOW(6),cancelled_by=9001,cancel_reason='Migration verification' WHERE id=9001;")
    assert sql("SELECT COUNT(*) FROM booking_seats WHERE booking_id=9001 AND booking_status='CANCELLED' AND active_seat_id IS NULL AND unit_price=80000;") == '1'
    assert sql('SELECT COUNT(*) FROM bookings WHERE id=9001;') == '1'
    print(json.dumps({'database': args.database, 'migration': 'V001', 'legacyHistoryPreserved': True,
                      'legacyCancellationWorks': True, 'fixtureRetained': True}))


if __name__ == '__main__':
    main()
