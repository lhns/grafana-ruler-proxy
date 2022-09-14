FROM openjdk:18

COPY target/scala-*/*.sh.bat ./

CMD exec ./*.sh.bat
