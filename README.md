# URL SHORTNER

A URL Shortner made with Play Framework using Scala. It provides a REST API to shorten long URLs and redirect the shortcodes back to the original URL.

## Technologies Used
* **Scala**
* **Play Framework**: For building the web application and REST API.
* **MYSQL**: For database.
* **gRPC**: For micro-service communication with the notification service.
* **Docker**: For running the application as containers.
* **Redis**: For rate limiting.

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
    sbt Docker/publishLocal
    ```
    
3.  **Run the application with Docker Compose**:
    This command will start all the services from `docker-compose.yml` file
    ```sh
    docker-compose up
    ```
---

### API ENDPOINTS

1. **Shorten a URL**:
    Creates a new short URL
    - URL: `/urls`
    - Method: `POST`
    - Request Body:
         ```json
        { 
            "url" : "https://example.com/"
        }
        ```

    - Response (201 Created):
        ```json
        {
            "message": "Url Created successfully",
            "data": {
                "id": 1,
                "short_code": "5eQsiTg",
                "long_url": "https://example.com/",
                "clicks": 0,
                "created_at": 1757139007000,
                "updated_at": 1757139007000
            }
        }
        ```
      
2. **Redirect to Original URL**
    Redirects to the original URL using the shortcode
    - URL: `/:shortcode`
    - Method: `GET`
    - Example: `GET http://localhost:9000/5eQsiTg`
    - Response: A `307 Temporary Redirect` to the Original URL

3. **Get All URLs**
    Retrieves a list of all URLs
    - URL: `/urls`
    - Method: `GET`
    - Response (200 OK):
        ```json
        {
        "message": "List of Urls",
        "urls": [
            {
                "id": 1,
                "short_code": "5eQsiTg",
                "long_url": "https://example.com",
                "clicks": 0,
                "created_at": 1757139007000,
                "updated_at": 1757139007000
            }
        ]
        }
        ```
4. **Get Url By Short Code**
    Retrieves a Url by shortcode
    - URL: `/urls/5eQsiTg`
    - Method: `GET`
    - Response (200 OK):
        ```json
        {
           "message": "Url with shortcode 5eQsiTg",
           "data": {
              "id": 1,
              "short_code": "5eQsiTg",
              "long_url": "https://example.com",
              "clicks": 0,
              "created_at": 1757139007000,
              "updated_at": 1757139007000
           }
       }
        ```
    - Response (404 Not Found):
        ```json
        {
           "message": "Unable to find Url with shortcode 5eQsiTg"
        }
        ```

5. **Delete Url By Short Code**
    Deletes a Url by Short Code
    - URL: `/urls/5eQsiTg`
    - Method: `DELETE`
    - Response (204 No Content)
    - Response (404 Not Found):
        ```json
        {
           "message": "Unable to find Url with shortcode 5eQsiTg"
        }
        ```
6. **Get All Notifications**
    Retrieves All Notifications
    - URL: `/notifications`
    - Method: `GET`
    - Response (200 OK):
        ```json
        {
            "message": "List of all Notifications",
            "notifications": [
                {
                    "id": 1,
                    "short_code": "5eQsiTg",
                    "notificationType": "NEWURL",
                    "notificationStatus" : "SUCCESS",
                    "message": "URL Created for https://example.com/"
                }
            ]
        }
        ```
