FROM openjdk:21

COPY target/scala-*/*.sh.bat ./

CMD exec ./*.sh.bat
