package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuditResourceTest {

    @Test
    void auditEndpointReturnsListShape() {

        given().when().get("/agentican/audit")
                .then()
                .statusCode(200)
                .body("$", notNullValue());
    }

    @Test
    void auditEndpointAcceptsFilters() {

        given().queryParam("entityType", "agent")
                .queryParam("limit", 10)
                .when().get("/agentican/audit")
                .then()
                .statusCode(200);
    }

    @Test
    void auditEndpointTolerantOfBadSince() {

        given().queryParam("since", "not-a-timestamp")
                .when().get("/agentican/audit")
                .then()
                .statusCode(200);
    }

    @Test
    void pruneWithoutCutoffReturns400() {

        given().when().delete("/agentican/audit")
                .then().statusCode(400);
    }

    @Test
    void pruneWithOlderThanAcceptsIsoDuration() {

        given().queryParam("olderThan", "P365D")
                .when().delete("/agentican/audit")
                .then()
                .statusCode(200)
                .body("deleted", notNullValue());
    }

    @Test
    void pruneWithInvalidOlderThanReturns400() {

        given().queryParam("olderThan", "not-a-duration")
                .when().delete("/agentican/audit")
                .then().statusCode(400);
    }
}
