FROM eclipse-temurin:26

COPY target/scala-*/*.sh.bat ./

CMD exec ./*.sh.bat
