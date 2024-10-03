FROM amazoncorretto:17-alpine3.18-jdk
LABEL maintainer="Josiah Adetayo <josleke@gmail.com> | <josiah.adetayo@meld-tech.com>"
#Add volume pointing to /tmp
VOLUME /tmp
# Run system update
RUN apk update && apk upgrade
#Remove unused packages and the cache- /etc/cache/apk for apk, and /var/cache/yum for yum package managers
RUN apk cache -v sync
# Set the working directory to /app
WORKDIR /app
# Copy the fat jar into the work directory of the container
COPY build/libs/Authorizer-Service-0.0.5.jar /app/auth-service.jar
#execute the application
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","auth-service.jar"]