<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
		 					 https://maven.apache.org/xsd/maven-4.0.0.xsd">

	<modelVersion>4.0.0</modelVersion>

	<!--
		Spring Boot Parent
		Manages ALL dependency versions automatically.
		You never need to specify versions for Spring libraries.
		3.2.5 is a stable LTS release — do not change this.
	-->
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.2.5</version>
		<relativePath/>
	</parent>

	<!--
		YOUR PROJECT IDENTITY
		These must match what Spring Initializr generated for you.
		groupId:    com.mostak
		artifactId: chatroom
		Check your original pom.xml — keep whatever Initializr gave you.
	-->
	<groupId>com.mostak</groupId>
	<artifactId>chatroom</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>chatroom</name>
	<description>Real-time chatroom with WebSocket and RabbitMQ</description>

	<properties>
		<!--
			Java version.
			Spring Boot 3.x requires Java 17 minimum.
			Make sure your IntelliJ project SDK is also set to Java 17+.
			Check: File → Project Structure → Project → SDK
		-->
		<java.version>17</java.version>
	</properties>

	<dependencies>

		<!-- ═══════════════════════════════════════════════════════
			 1. SPRING WEB
			 Gives us: embedded Tomcat + Spring MVC.
			 Needed for REST endpoints (GET /api/messages etc.)
			 and as the base for WebSocket (handshake starts as HTTP).
		     ═══════════════════════════════════════════════════════ -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>

		<!-- ═══════════════════════════════════════════════════════
			 2. WEBSOCKET + STOMP
			 Gives us: WebSocket support, STOMP protocol, SockJS.
			 This is what handles browser ↔ Spring Boot real-time
			 communication.
			 @MessageMapping, @SendTo, SimpMessagingTemplate
			 all come from this dependency.
		     ═══════════════════════════════════════════════════════ -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-websocket</artifactId>
		</dependency>

		<!-- ═══════════════════════════════════════════════════════
			 3. RABBITMQ / AMQP  ← THE KEY ONE FOR THIS STEP
			 AMQP = Advanced Message Queuing Protocol.
			 RabbitMQ implements AMQP.

			 This gives us:
			   - RabbitTemplate       → publish messages to RabbitMQ
			   - @RabbitListener      → consume messages from RabbitMQ
			   - ConnectionFactory    → manages the connection to RabbitMQ
			   - Jackson2JsonConverter → auto JSON serialization
			   - Auto-configuration   → reads spring.rabbitmq.* properties

			 WITHOUT this dependency:
			   - Spring Boot ignores all spring.rabbitmq.* properties
			   - RabbitMQConfig.java fails to compile
			   - No connection to RabbitMQ is ever attempted
			   - chat.exchange never gets created
		     ═══════════════════════════════════════════════════════ -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-amqp</artifactId>
		</dependency>

		<!-- ═══════════════════════════════════════════════════════
			 4. JPA + HIBERNATE
			 Gives us: ORM (Object Relational Mapping).
			 Maps our ChatMessage Java class ↔ chat_messages DB table.
			 @Entity, @Id, @Column, JpaRepository all come from here.

			 Without this:
			   - @Entity annotation is unrecognized
			   - JpaRepository cannot be extended
			   - No database persistence
		     ═══════════════════════════════════════════════════════ -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>

		<!-- ═══════════════════════════════════════════════════════
			 5. H2 IN-MEMORY DATABASE
			 A lightweight database that lives in RAM.
			 No installation needed — it starts with Spring Boot.
			 Data resets on every restart (perfect for learning).

			 scope=runtime means:
			   - Available when the app runs
			   - NOT needed to compile the code
			   - Not included in the final JAR's compile classpath

			 Without this:
			   - Spring Boot can't find a database driver
			   - App fails to start with DataSource error
		     ═══════════════════════════════════════════════════════ -->
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>runtime</scope>
		</dependency>

		<!-- ═══════════════════════════════════════════════════════
			 6. LOMBOK
			 Eliminates Java boilerplate via annotations:
			   @Data              → getters, setters, equals, hashCode, toString
			   @Builder           → builder pattern
			   @NoArgsConstructor → no-argument constructor
			   @AllArgsConstructor → all-fields constructor
			   @RequiredArgsConstructor → constructor for final fields
			   @Slf4j             → logger field

			 optional=true means Lombok is only needed at compile time.
			 It is NOT included in the final JAR.

			 IMPORTANT — After Maven syncs, do this in IntelliJ:
			   Settings → Build → Compiler → Annotation Processors
			   → check "Enable annotation processing" → OK
			   Then: Build → Rebuild Project

			 Without annotation processing enabled:
			   IntelliJ shows red errors on @Data, @Builder etc.
			   even though the code compiles correctly via Maven.
		     ═══════════════════════════════════════════════════════ -->
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>

		<!-- ═══════════════════════════════════════════════════════
			 7. TEST
			 JUnit 5 + Mockito + Spring Test utilities.
			 scope=test = only available during mvn test phase.
			 Spring Initializr always adds this — leave it as-is.
		     ═══════════════════════════════════════════════════════ -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>

	</dependencies>

	<build>
		<plugins>
			<!--
				Spring Boot Maven Plugin
				- Packages the app into a runnable fat JAR
				  (includes all dependencies + embedded Tomcat)
				- Enables: mvn spring-boot:run
				- The <excludes> block removes Lombok from the final JAR
				  since Lombok is only needed at compile time
			-->
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
				<configuration>
					<excludes>
						<exclude>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
						</exclude>
					</excludes>
				</configuration>
			</plugin>
		</plugins>
	</build>

</project>
