# URL SHORTNER

A URL Shortner made with Play Framework using Scala. It provides a REST API to shorten long URLs and redirect the shortcodes back to the original URL.

## Technologies Used
* **Scala**
* **Play Framework**: For building the web application and REST API.
* **MYSQL**: For database.
* **gRPC**: For micro-service communication with the notification service.
* **Docker**: For running the application as containers.

---

## Getting Started

### Prerequisites
* **Docker** and **Docker Compose** must be installed.

1.  **Clone the repository**:
    ```sh
    git clone https://github.com/PraaneshSelvaraj/urlshortner
    cd urlshortner
    ```

2.  **Build the Docker image for the Rest Service**:
    Before running the services, you need to build the Docker images for both the `rest-service` and `notification-service`
    ```sh
    sbt "project rest-service" Docker/publishLocal
    sbt "project notification-service" Docker/publishLocal
    ```
    
3.  **Run the application with Docker Compose**:
    This command will start all the services from `docker-compose.yml` file
    ```sh
    docker-compose up
    ```
---
