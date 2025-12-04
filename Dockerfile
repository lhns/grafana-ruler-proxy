FROM eclipse-temurin:25

COPY target/scala-*/*.sh.bat ./

CMD exec ./*.sh.bat
