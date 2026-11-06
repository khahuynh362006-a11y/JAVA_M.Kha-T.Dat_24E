param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArguments)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $env:JAVA_HOME -or -not(Test-Path -LiteralPath "$env:JAVA_HOME/bin/java.exe")){
  $jdk=Get-ChildItem 'C:/Program Files/Java' -Directory -ErrorAction SilentlyContinue | Where-Object Name -Like 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1
  if($jdk){$env:JAVA_HOME=$jdk.FullName}else{throw 'Install Java 21 and set JAVA_HOME.'}
}
$maven=(Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if(-not $maven){$maven=Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists/apache-maven-3.9*') -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName}
if(-not $maven){
  $toolRoot=Join-Path $projectRoot '.tools';New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null
  $version='3.9.11';$archive=Join-Path $toolRoot "apache-maven-$version-bin.zip";$url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
  Invoke-WebRequest -Uri $url -OutFile $archive
  $expected=((Invoke-WebRequest -Uri "$url.sha512").Content).Trim().Split(' ')[0]
  if((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash.ToLowerInvariant() -ne $expected.ToLowerInvariant()){throw 'Maven archive checksum mismatch'}
  Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot -Force
  $maven=Join-Path $toolRoot "apache-maven-$version/bin/mvn.cmd"
}
& $maven -f (Join-Path $projectRoot 'backend/pom.xml') @MavenArguments
if($LASTEXITCODE -ne 0){throw "Maven failed with exit code $LASTEXITCODE"}
