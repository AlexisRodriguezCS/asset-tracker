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
 * The automated demo: sign in as the seeded tech, check an asset out to a person, confirm it shows
 * on that person, reject a double check-out, then run offboarding. Also covers the authorization
 * rules - nothing is readable without a token, and an ordinary employee sees only their own gear.
 *
 * <p>When the gateway is unreachable this self-skips (JUnit assumption) so a plain {@code ./gradlew
 * build} stays green with no stack running - <b>unless {@code -De2e.required=true} is set</b>,
 * which turns the same check into a hard failure. CI's e2e stage sets it: a suite that silently
 * skips itself in the one place it is supposed to run is worse than no suite at all.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class PlatformE2ETest {

  private static final long ACME = 1L;
  private static final String PASSWORD = "Passw0rd!";

  private String token;
  private long personId;
  private long assetId;

  @BeforeAll
  void gatewayMustBeRouting() {
    RestAssured.baseURI = System.getProperty("e2e.baseUrl", "http://localhost:8080");

    // Signing in is the readiness probe: it proves the gateway is routing to a live
    // auth-service. A catalog GET cannot serve that purpose any more - it is rejected
    // at the gateway before any downstream service is involved.
    int status;
    try {
      status = login().thenReturn().statusCode();
    } catch (Exception notReachable) {
      status = -1;
    }

    String where = "gateway routing at " + RestAssured.baseURI;
    if (Boolean.getBoolean("e2e.required")) {
      assertThat(status).as("%s (e2e.required=true, so this is a failure)", where).isEqualTo(200);
    } else {
      assumeThat(status).as(where).isEqualTo(200);
    }
  }

  private io.restassured.response.Response login() {
    return given()
        .contentType(JSON)
        .body(Map.of("email", "tech@acme.example", "password", PASSWORD))
        .post("/api/auth/login");
  }

  @Test
  @Order(1)
  void nothingIsReadableWithoutAToken() {
    given().get("/api/assets?clientId=" + ACME).then().statusCode(401);
    given().get("/api/people?clientId=" + ACME).then().statusCode(401);
  }

  @Test
  @Order(2)
  void signInAsTech() {
    token = login().then().statusCode(200).body("token", notNullValue()).extract().path("token");
    assertThat(token).isNotBlank();
  }

  @Test
  @Order(3)
  void pickAPersonAndAnInStockAsset() {
    personId =
        authed()
            .get("/api/people?clientId=" + ACME)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getLong("[0].id");

    JsonPath assets =
        authed()
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
    authed()
        .get("/api/assets?clientId=" + ACME + "&holderType=PERSON&holderId=" + personId)
        .then()
        .statusCode(200)
        .body("id", hasItem((int) assetId));
  }

  @Test
  @Order(6)
  void doubleCheckOutIsRejected() {
    long other =
        authed().get("/api/people?clientId=" + ACME).then().extract().jsonPath().getLong("[1].id");
    authed()
        .contentType(JSON)
        .body(
            Map.of("clientId", ACME, "assetId", assetId, "holderType", "PERSON", "holderId", other))
        .post("/api/assignments")
        .then()
        .statusCode(409);
  }

  /**
   * The employee-visibility rule, end to end: Dana holds the asset the tech just checked out to
   * her, and signing in as her shows that asset and nothing else - even though the tenant has ~59.
   */
  @Test
  @Order(7)
  void anEmployeeSeesOnlyTheirOwnGear() {
    String hers =
        given()
            .contentType(JSON)
            .body(Map.of("email", "dana.reyes@acme.example", "password", PASSWORD))
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");

    List<Object> visible =
        given()
            .header("Authorization", "Bearer " + hers)
            .get("/api/assets?clientId=" + ACME)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("holderId");

    // whatever came back is hers alone
    assertThat(visible).isNotEmpty().allMatch(h -> Long.valueOf(personId).equals(toLong(h)));
  }

  @Test
  @Order(8)
  void offboardingCollectsEverything() {
    authed()
        .post("/api/assignments/offboard?clientId=" + ACME + "&personId=" + personId)
        .then()
        .statusCode(200)
        .body("returned", hasItem((int) assetId));

    authed().get("/api/assets/" + assetId).then().statusCode(200).body("status", is("IN_STOCK"));
  }

  private static Long toLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  private RequestSpecification authed() {
    return given().header("Authorization", "Bearer " + token);
  }
}
