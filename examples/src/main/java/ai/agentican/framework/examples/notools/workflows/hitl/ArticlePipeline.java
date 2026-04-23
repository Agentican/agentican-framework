/**
 * Domain: Content / Editorial
 * Tools: None
 * HITL: Question on the intermediate `draft` step — the writer asks for
 *       audience and tone before drafting, since a developer article and an
 *       exec article can share the same outline but diverge on every
 *       paragraph.
 */
package ai.agentican.framework.examples.notools.workflows.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.notools.agents.hitl.CliHitlNotifier;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class ArticlePipeline {

    static String TASK_NAME = "Produce Streaming Migration Article";
    static String PLAN_NAME = "Article Pipeline";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var pipeline = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(ArticleRequest.class)
                    .output(PolishedArticle.class)
                    .build();

            var article = pipeline.runAsync(request()).join();

            print(article);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(ArticlePipeline.class.getResource("/article-pipeline.yaml")).toURI());
    }

    static ArticleRequest request() {

        return new ArticleRequest(
                "How we cut ingestion latency from 40 minutes to 90 seconds by "
                        + "rebuilding on a streaming-first schema registry.",
                1400);
    }

    static void print(PolishedArticle a) {

        System.out.println("Title: " + a.title() + "\n");
        System.out.println(a.body());
    }

    record ArticleRequest(

            @JsonPropertyDescription("Article topic")
            String topic,

            @JsonPropertyDescription("Target word count")
            int wordBudget) {
    }

    record PolishedArticle(

            @JsonPropertyDescription("Final article title in sentence case")
            String title,

            @JsonPropertyDescription("Article body — edited, pace-corrected, marketing hedges stripped")
            String body) {
    }
}
