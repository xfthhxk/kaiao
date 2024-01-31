FROM bellsoft/liberica-openjre-alpine:21.0.1-12-cds
COPY target/kaiao-uber.jar /app/kaiao.jar
EXPOSE 8080
RUN adduser -D kaiao
WORKDIR /app
RUN chown -R kaiao /app
USER kaiao
CMD ["java", "-jar", "/app/kaiao.jar", "--main", "kaiao.main"]