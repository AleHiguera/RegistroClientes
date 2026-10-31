@echo off
if not exist out mkdir out
javac -encoding UTF-8 -cp "lib/*" -d out src\comun\*.java src\servidor\*.java src\cliente\*.java
if %errorlevel%==0 echo Compilado en la carpeta out
