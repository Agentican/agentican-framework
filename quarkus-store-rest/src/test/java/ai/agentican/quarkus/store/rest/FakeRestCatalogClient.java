package ai.agentican.quarkus.store.rest;

import ai.agentican.quarkus.store.rest.dto.RestAgentView;
import ai.agentican.quarkus.store.rest.dto.RestSkillView;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class FakeRestCatalogClient implements RestCatalogClient {

    private final String plansJson;
    private final List<RestAgentView> agents;
    private final List<RestSkillView> skills;
    private final AtomicBoolean failOnPlans = new AtomicBoolean(false);

    FakeRestCatalogClient(String plansJson, List<RestAgentView> agents, List<RestSkillView> skills) {

        this.plansJson = plansJson;
        this.agents = agents;
        this.skills = skills;
    }

    void failNextPlansCall() { failOnPlans.set(true); }

    @Override
    public String listPlansJson() {

        if (failOnPlans.getAndSet(false))
            throw new RuntimeException("simulated catalog outage");

        return plansJson;
    }

    @Override
    public String getPlanJson(String id) { return plansJson; }

    @Override
    public List<RestAgentView> listAgents() { return agents; }

    @Override
    public RestAgentView getAgent(String ref) { return null; }

    @Override
    public List<RestSkillView> listSkills() { return skills; }

    @Override
    public RestSkillView getSkill(String ref) { return null; }
}
