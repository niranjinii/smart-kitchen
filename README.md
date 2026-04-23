# Smart Kitchen

Smart Kitchen is a community-driven culinary platform designed to bring food lovers together while offering a highly personalized cooking experience. Rather than just acting as a static recipe book, the app adapts every meal to fit *you*. Connect with other home chefs, explore a shared feed of culinary creations, and let the intelligent engine seamlessly tailor every recipe to your specific dietary rules, current pantry stock, and serving needs.

## Key Features

* **Community Connection:** Discover recipes uploaded by other users, leave ratings and reviews, add personal Chef's Notes, and bookmark your favorite community creations.
* **Deep Personalization:** Set global dietary preferences (e.g., automatically swap cow's milk for oat milk) that instantly apply to recipe calculations across the entire platform so every meal fits your lifestyle.
* **Intelligent Recommendation Engine:** Scans your personal pantry stock against the community's recipe book to calculate and suggest the best meals you can cook right now.
* **Interactive Cook Mode:** A distraction-free, step-by-step cooking interface featuring voice-activated controls, auto-generated smart timers, and dynamic ingredient tracking.
* **Recipe Management & Scaling:** Create and categorize custom recipes with a dynamic yield adjuster that instantly scales ingredient math based on your desired serving size.
* **Smart Shopping List:** Automatically calculates missing ingredients from your planned recipes, converts units, and aggregates them into a clean, consolidated grocery list.
* **Virtual Pantry:** Track your physical kitchen inventory, including custom measurements, categories, and expiration dates.
* **Web Scraper Importer:** Paste a URL from any public recipe blog to automatically extract the title, ingredients, instructions, and images directly into your collection to share with the community.

## Tech Stack

* **Backend:** Java 17, Spring Boot 3.x (Web, Data JPA, Security)
* **Frontend:** Thymeleaf, HTML5, CSS3, Vanilla JavaScript, Bootstrap 5
* **Database:** PostgreSQL (hosted via Neon)
* **Image Storage:** Cloudinary
* **Web Scraping:** JSoup & Jackson DataBind (JSON-LD parsing)

## Setup and Installation

### 1. Prerequisites
* Java 17 or higher installed on your machine.
* Maven 3.x (or use the included Maven wrapper).

### 2. External Services Setup
To run this application locally, you must provision your own instances for the database and image hosting. 

* **Database (Neon PostgreSQL):**
  1. Create a free account on [Neon.tech](https://neon.tech/).
  2. Create a new project and a PostgreSQL database.
  3. Copy your database connection string.

* **Image Hosting (Cloudinary):**
  1. Create a free account on [Cloudinary](https://cloudinary.com/).
  2. Locate your Cloud Name, API Key, and API Secret on your developer dashboard.

### 3. Environment Variables
Create a file named `application-secrets.properties` in the `src/main/resources/` directory. Add your specific service credentials to this file. Note: This file is included in the `.gitignore` to prevent accidental credential exposure if you push to a public repository.

```properties
# Database Configuration (Neon PostgreSQL)
spring.datasource.url=jdbc:postgresql://<your-neon-host>.neon.tech/<your-db-name>?sslmode=require
spring.datasource.username=<your-db-username>
spring.datasource.password=<your-db-password>

# Cloudinary Configuration
cloudinary.cloud-name=<your-cloud-name>
cloudinary.api-key=<your-api-key>
cloudinary.api-secret=<your-api-secret>
```

### 4. Running the Application
Open your terminal, navigate to the project root directory, and execute the following command:

**On Mac/Linux:**
```bash
./mvnw spring-boot:run
```

**On Windows:**
```cmd
mvnw.cmd spring-boot:run
```

Once the application has successfully started, open your web browser and navigate to `http://localhost:8080`.