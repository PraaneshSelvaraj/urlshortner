# URL SHORTNER

A URL Shortner made with Play Framework using Scala. It provides a REST API to shorten long URLs and redirect the shortcodes back to the original URL.

## Technologies Used

* **Scala**
* **Play Framework**: For building the web application and REST API.
* **MYSQL**: For database.
* **gRPC**: For micro-service communication with the notification service.
* **Docker**: For running the application as containers.
* **Redis**: For rate limiting.

## Getting Started

### Prerequisites

* **Docker** and **Docker Compose** must be installed.

1. **Clone the repository**:

    ```sh
    git clone https://github.com/PraaneshSelvaraj/urlshortner
    cd urlshortner
    ```

2. **Build the Docker image for the Rest Service**:
    Before running the services, you need to build the Docker images for both the `rest-service` and `notification-service`

    ```sh
    sbt Docker/publishLocal
    ```

3. **Run the application with Docker Compose**:
    This command will start all the services from `docker-compose.yml` file

    ```sh
    docker-compose up
    ```

### API ENDPOINTS

1. **Shorten a URL:**:
    Creates a new short URL
    * URL: `/urls`
    * Method: `POST`
    * Request Body:

         ```json
        { 
            "url" : "https://example.com/"
        }
        ```

    * Response (201 Created):

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

2. **Redirect to Original URL:**
    Redirects to the original URL using the shortcode
    * URL: `/:shortcode`
    * Method: `GET`
    * Example: `GET http://localhost:9000/5eQsiTg`
    * Response: A `307 Temporary Redirect` to the Original URL

3. **Get All URLs:**
    Retrieves a list of all URLs
    * URL: `/urls`
    * Method: `GET`
    * Response (200 OK):

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

4. **Get Url By Short Code:**
    Retrieves a Url by shortcode
    * URL: `/urls/5eQsiTg`
    * Method: `GET`
    * Response (200 OK):

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

    * Response (404 Not Found):

        ```json
        {
           "message": "Unable to find Url with shortcode 5eQsiTg"
        }
        ```

5. **Delete Url By Short Code:**
    Deletes a Url by Short Code
    * URL: `/urls/5eQsiTg`
    * Method: `DELETE`
    * Response (204 No Content)
    * Response (404 Not Found):

        ```json
        {
           "message": "Unable to find Url with shortcode 5eQsiTg"
        }
        ```

6. **Get All Notifications:**
    Retrieves All Notifications
    * URL: `/notifications`
    * Method: `GET`
    * Response (200 OK):

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

7. **Create User:**
    Creates a new user
    * URL: `/user`
    * Method: `POST`
    * Request Body:

         ```json
        { 
            "username" : "user",
            "email" : "user@gmail.com",
            "password": "user@12345"
        }
        ```

    * Response (200 OK):

        ```json
        {
            "message": "User Created",
            "data": {
                "id": 1,
                "username": "user",
                "email": "user@gmail.com",
                "password": "$2a$10$whDZI03pauJJi5xOOgnqqOLSjSSenO3Z7EHqNmuMX90MHZvzbLqai",
                "role": "USER",
                "auth_provider": "LOCAL",
                "is_deleted": false,
                "created_at": 1761547792261,
                "updated_at": 1761547792261
            }
        }
        ```

8. **User Login:**
    Login as Normal user
    * URL: `/auth/login`
    * Method: `POST`
    * Request Body:

         ```json
        { 
            "email" : "user@gmail.com",
            "password": "user@12345"
        }
        ```

    * Response (200 OK):

        ```json
        {
            "message": "Login was successfull",
            "accessToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NjE1NTk4MjYsImlhdCI6MTc2MTU1NjIyNiwiZW1haWwiOiJ0ZXN0QGdtYWlsLmNvbSIsInJvbGUiOiJVU0VSIn0.yPMKse382rTFHgq41LA8DqaVNsDJWUlbEM1MTvywenQ",
            "refreshToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NjIxNjEwMjYsImlhdCI6MTc2MTU1NjIyNiwiZW1haWwiOiJ0ZXN0QGdtYWlsLmNvbSIsInJvbGUiOiJVU0VSIiwidHlwZSI6InJlZnJlc2gifQ.yJAF0GWxNCLGgYKtdLUKrLzswvPFe6iqj2oLrGDUcbg"
        }
        ```

9. **Google User Login:**
    Login as Google User
    * URL: `/auth/google/login`
    * Method: `POST`
    * Request Body:

         ```json
        { 
            "idToken" : "Id Token after login (frontend)"
        }
        ```

    * Response (200 OK):

        ```json
        {
            "success": true,
            "message": "Account created and login successful",
            "accessToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NjE1NjA0NzYsImlhdCI6MTc2MTU1Njg3NiwiZW1haWwiOiJwcmFhbmVzaHNlbHZhcmFqMjAwM0BnbWFpbC5jb20iLCJyb2xlIjoiVVNFUiJ9.lVc7ZBAslVK-FmJMLGcY3WTqQLkzUalzIzxbgzTNYLg",
            "refreshToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NjIxNjE2NzYsImlhdCI6MTc2MTU1Njg3NiwiZW1haWwiOiJwcmFhbmVzaHNlbHZhcmFqMjAwM0BnbWFpbC5jb20iLCJyb2xlIjoiVVNFUiIsInR5cGUiOiJyZWZyZXNoIn0.SQxcTtwbFsytYdGykTg4BWqg-HigQUmJ0rJ_2f9IYmg",
            "user": {
                "id": 3,
                "username": "Praanesh Selvaraj",
                "email": "praaneshselvaraj2003@gmail.com",
                "role": "USER",
                "authProvider": "GOOGLE"
            }
        }
        ```

10. **Refresh Tokens:**
    Generate new access token and refresh token
    * URL: `/auth/refresh`
    * Method: `POST`
    * Response (200 OK):

        ```json
        {
            "message": "Tokens refreshed successfully.",
            "accessToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NjE1NjA2NTEsImlhdCI6MTc2MTU1NzA1MSwiZW1haWwiOiJwcmFhbmVzaHNlbHZhcmFqMjAwM0BnbWFpbC5jb20iLCJyb2xlIjoiVVNFUiJ9.bCCgM0QQfy3aSoarVR-dD5uFezrgfmNlwoRs6vKSNtI",
            "refreshToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NjIxNjE4NTEsImlhdCI6MTc2MTU1NzA1MSwiZW1haWwiOiJwcmFhbmVzaHNlbHZhcmFqMjAwM0BnbWFpbC5jb20iLCJyb2xlIjoiVVNFUiIsInR5cGUiOiJyZWZyZXNoIn0.KJ9-paCYdjUakyA_JXj9KBBB_VekjyoqG6Sf1d8ZUWA"
        }
        ```

11. **Get User:**
    Retrieves an user by ID
    * URL: `/user/:id`
    * Method: `POST`
    * Response (200 OK):

        ```json
        {
            "message": "User with Id: 1 was fetched.",
            "data": {
                "id": 1,
                "username": "user",
                "email": "user@gmail.com",
                "password": "$2a$10$o6v0teedpLKmS4miI04KF.AJPUT8j2V8Mi7jUZxL4R41d7DTXbPji",
                "role": "USER",
                "auth_provider": "LOCAL",
                "is_deleted": false,
                "created_at": 1761556183000,
                "updated_at": 1761556183000
            }
        }
        ```

12. **Delete User:**
    Deletes a User
    * URL: `/urls/:id`
    * Method: `DELETE`
    * Response (204 No Content)
