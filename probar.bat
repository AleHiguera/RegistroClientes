@echo off
if not exist out mkdir out
javac -encoding UTF-8 -cp "lib/*" -d out src\comun\*.java src\servidor\*.java src\cliente\*.java test\servidor\*.java
if %errorlevel% neq 0 exit /b %errorlevel%
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "out;lib/*" servidor.Pruebas
