FROM eclipse-temurin:25-jre

WORKDIR /app

COPY hypergravel-proxy/build/libs/hypergravel-proxy-*-all.jar /app/hypergravel.jar

RUN mkdir -p /app/config/extensions

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxDirectMemorySize=2G \
 -Dio.netty.allocator.type=pooled -Dio.netty.leakDetection.level=disabled"

EXPOSE 25565

VOLUME ["/app/config"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/hypergravel.jar /app/config"]