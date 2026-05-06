package ai.agentican.framework.registry;

import ai.agentican.framework.config.SkillConfig;

public interface SkillRegistry extends Catalog<SkillConfig> {

    default void seed() { }
}
