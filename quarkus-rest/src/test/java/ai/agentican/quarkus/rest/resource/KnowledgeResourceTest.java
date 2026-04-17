package ai.agentican.quarkus.rest.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class KnowledgeResourceTest {

    @Test
    void createAndGetEntry() {

        var id = given()
                .contentType("application/json")
                .body("{\"name\": \"Test Entry\", \"description\": \"A test knowledge entry\"}")
                .when().post("/agentican/knowledge")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Test Entry"))
                .body("status", equalTo("INDEXED"))
                .extract().jsonPath().getString("id");

        given()
                .when().get("/agentican/knowledge/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("Test Entry"))
                .body("description", equalTo("A test knowledge entry"));
    }

    @Test
    void addFactToEntry() {

        var id = given()
                .contentType("application/json")
                .body("{\"name\": \"Facts Entry\"}")
                .when().post("/agentican/knowledge")
                .then()
                .statusCode(201)
                .extract().jsonPath().getString("id");

        given()
                .contentType("application/json")
                .body("{\"name\": \"Key fact\", \"content\": \"This is important\", \"tags\": [\"domain/topic\"]}")
                .when().post("/agentican/knowledge/" + id + "/facts")
                .then()
                .statusCode(200)
                .body("name", equalTo("Key fact"))
                .body("content", equalTo("This is important"))
                .body("tags", hasSize(1));

        given()
                .when().get("/agentican/knowledge/" + id)
                .then()
                .statusCode(200)
                .body("facts", hasSize(1))
                .body("facts[0].name", equalTo("Key fact"));
    }

    @Test
    void deleteEntry() {

        var id = given()
                .contentType("application/json")
                .body("{\"name\": \"To Delete\"}")
                .when().post("/agentican/knowledge")
                .then()
                .statusCode(201)
                .extract().jsonPath().getString("id");

        given()
                .when().delete("/agentican/knowledge/" + id)
                .then()
                .statusCode(204);

        given()
                .when().get("/agentican/knowledge/" + id)
                .then()
                .statusCode(404);
    }

    @Test
    void getUnknownEntryReturns404() {

        given()
                .when().get("/agentican/knowledge/nonexistent")
                .then()
                .statusCode(404)
                .body("code", equalTo("not_found"));
    }

    @Test
    void createWithoutNameReturns400() {

        given()
                .contentType("application/json")
                .body("{\"description\": \"no name\"}")
                .when().post("/agentican/knowledge")
                .then()
                .statusCode(400);
    }

    @Test
    void listReturnsCreatedEntries() {

        given()
                .contentType("application/json")
                .body("{\"name\": \"List Test Entry\"}")
                .when().post("/agentican/knowledge")
                .then()
                .statusCode(201);

        given()
                .when().get("/agentican/knowledge")
                .then()
                .statusCode(200)
                .body("name", org.hamcrest.Matchers.hasItem("List Test Entry"));
    }
}
