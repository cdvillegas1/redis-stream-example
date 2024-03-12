FROM postgres:16

# Copy SQL script into the container
COPY init.sql /docker-entrypoint-initdb.d/

# Environment variables
ENV POSTGRES_DB company
ENV POSTGRES_USER random
ENV POSTGRES_PASSWORD code