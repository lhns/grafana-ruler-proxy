FROM openjdk:17

COPY target/scala-*/*.sh.bat ./

CMD exec ./*.sh.bat
