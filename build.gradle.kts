plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.scorestv"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// --- Spring Boot starters ---
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket")

	// --- Veritabanı ---
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	// --- JWT / güvenlik ---
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	// Google ID token dogrulama (JWKS tabanli JWT decoder)
	implementation("org.springframework.security:spring-security-oauth2-jose")

	// --- Lombok + MapStruct (annotation processing) ---
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
	implementation("org.mapstruct:mapstruct:1.6.3")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

	// --- API dokümantasyonu (Swagger / OpenAPI) ---
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

	// --- Nesne depolama (MinIO / S3 uyumlu) ---
	implementation("io.minio:minio:8.5.7")

	// --- Excel (çeviri export/import: .xlsx üretme ve okuma) ---
	implementation("org.apache.poi:poi-ooxml:5.4.0")

	// --- Firebase Cloud Messaging (push notifications) ---
	// Mobile uygulamaya FCM ile bildirim gondermek icin Firebase Admin SDK.
	// Service account JSON ile auth; bkz. FirebaseConfig.java.
	implementation("com.google.firebase:firebase-admin:9.4.2")

	// --- Elasticsearch (arama motoru) ---
	// Spring Data Elasticsearch + low-level RestClient. Index document'lari,
	// repository'ler ve search query'leri icin bkz. com.scorestv.search.*
	// Prod'da AYRI sunucuda calisir; baglanti detaylari env-driven.
	implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")

	// --- ShedLock (dagitik scheduled-task kilidi) ---
	// Birden fazla backend instance calistirildiginda @Scheduled isler her
	// node'da degil YALNIZCA bir node'da calissin diye Redis tabanli dagitik
	// kilit. @SchedulerLock ile isaretlenir; lock store Redis. 7.x = Spring
	// Boot 4 / Spring Framework 7 uyumlu.
	implementation("net.javacrumbs.shedlock:shedlock-spring:7.7.0")
	implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:7.7.0")

	// --- STOMP broker relay TCP client (Reactor Netty) ---
	// enableStompBrokerRelay (cok-instance WebSocket) dis broker'a TCP baglanti
	// icin Reactor Netty kullanir. Relay kapaliyken (SimpleBroker) kullanilmaz.
	implementation("org.springframework.boot:spring-boot-starter-reactor-netty")

	// --- Gelistirme ---
	developmentOnly("org.springframework.boot:spring-boot-devtools")
}
