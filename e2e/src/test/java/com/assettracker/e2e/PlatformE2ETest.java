package com.assettracker.e2e;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The automated demo: browse the catalog (public), sign in as the seeded tech, check an asset out
 * to a person, confirm it shows on that person, reject a double check-out, then run offboarding.
 * Self-skips (JUnit assumption) when the gateway is unreachable, so it is safe in a build with no
 * running stack.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class PlatformE2ETest {

  private static final long ACME = 1L;

  private String token;
  private long personId;
  private long assetId;

  @BeforeAll
  void gatewayMustBeRouting() {
    RestAssured.baseURI = System.getProperty("e2e.baseUrl", "http://localhost:8080");
    int status;
    try {
      status = given().get("/api/assets?clientId=" + ACME).thenReturn().statusCode();
    } catch (Exception notReachable) {
      status = -1;
    }
    assumeThat(status).as("gateway routing at %s", RestAssured.baseURI).isEqualTo(200);
  }

  @Test
  @Order(1)
  void catalogIsPublic() {
    given().get("/api/assets?clientId=" + ACME).then().statusCode(200);
    given().get("/api/people?clientId=" + ACME).then().statusCode(200);
  }

  @Test
  @Order(2)
  void signInAsTech() {
    token =
        given()
            .contentType(JSON)
            .body(Map.of("email", "tech@acme.example", "password", "Passw0rd!"))
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .body("token", notNullValue())
            .extract()
            .path("token");
    assertThat(token).isNotBlank();
  }

  @Test
  @Order(3)
  void pickAPersonAndAnInStockAsset() {
    personId =
        given().get("/api/people?clientId=" + ACME).then().extract().jsonPath().getLong("[0].id");

    JsonPath assets =
        given()
            .get("/api/assets?clientId=" + ACME + "&status=IN_STOCK")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();
    List<Object> ids = assets.getList("id");
    assertThat(ids).isNotEmpty();
    assetId = assets.getLong("[0].id");
  }

  @Test
  @Order(4)
  void checkOutToPerson() {
    authed()
        .contentType(JSON)
        .body(
            Map.of(
                "clientId", ACME,
                "assetId", assetId,
                "holderType", "PERSON",
                "holderId", personId))
        .post("/api/assignments")
        .then()
        .statusCode(201)
        .body("open", is(true));
  }

  @Test
  @Order(5)
  void assetNowShowsOnThePerson() {
    given()
        .get("/api/assets?clientId=" + ACME + "&holderType=PERSON&holderId=" + personId)
        .then()
        .statusCode(200)
        .body("id", hasItem((int) assetId));
  }

  @Test
  @Order(6)
  void doubleCheckOutIsRejected() {
    long other =
        given().get("/api/people?clientId=" + ACME).then().extract().jsonPath().getLong("[1].id");
    authed()
        .contentType(JSON)
        .body(
            Map.of("clientId", ACME, "assetId", assetId, "holderType", "PERSON", "holderId", other))
        .post("/api/assignments")
        .then()
        .statusCode(409);
  }

  @Test
  @Order(7)
  void offboardingCollectsEverything() {
    authed()
        .post("/api/assignments/offboard?clientId=" + ACME + "&personId=" + personId)
        .then()
        .statusCode(200)
        .body("returned", hasItem((int) assetId));

    given().get("/api/assets/" + assetId).then().statusCode(200).body("status", is("IN_STOCK"));
  }

  private RequestSpecification authed() {
    return given().header("Authorization", "Bearer " + token);
  }
}
