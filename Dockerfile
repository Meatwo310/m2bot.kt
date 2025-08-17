# escape=\
# syntax=docker/dockerfile:1

FROM openjdk:21-jdk-slim

# Create required directories
RUN mkdir -p /bot/plugins
RUN mkdir -p /bot/data
RUN mkdir -p /dist/out

# Declare required volumes
VOLUME [ "/bot/data" ]
VOLUME [ "/bot/plugins" ]

# Copy the distribution files into the container
COPY [ "build/distributions/m2bot-0.1.0.tar", "/dist" ]

# Extract the distribution files, and prepare them for use
RUN tar -xf /dist/m2bot-0.1.0.tar -C /dist/out
RUN chmod +x /dist/out/m2bot-0.1.0/bin/m2bot

# Clean up unnecessary files
RUN rm /dist/m2bot-0.1.0.tar

# Set the correct working directory
WORKDIR /bot

# Run the distribution start script
ENTRYPOINT [ "/dist/out/m2bot-0.1.0/bin/m2bot" ]
