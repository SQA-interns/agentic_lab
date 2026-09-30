package lab.conference.options;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable startup catalog (AR-07, BR-10). */
public final class Catalog {

  private final String conferenceTitle;
  private final ConsentFixture consent;
  private final Map<GroupId, List<CatalogOption>> groups;

  public Catalog(
      String conferenceTitle, ConsentFixture consent, Map<GroupId, List<CatalogOption>> groups) {
    this.conferenceTitle = conferenceTitle;
    this.consent = consent;
    Map<GroupId, List<CatalogOption>> copy = new EnumMap<>(GroupId.class);
    for (GroupId g : GroupId.values()) {
      copy.put(g, List.copyOf(groups.getOrDefault(g, List.of())));
    }
    this.groups = Collections.unmodifiableMap(copy);
  }

  public String conferenceTitle() {
    return conferenceTitle;
  }

  public Optional<ConsentFixture> consent() {
    return Optional.ofNullable(consent);
  }

  /** Active options of a group in configured order. */
  public List<CatalogOption> activeOptions(GroupId group) {
    return groups.get(group).stream().filter(CatalogOption::active).toList();
  }

  /** The option if it is active and belongs to the group. */
  public Optional<CatalogOption> activeOption(GroupId group, String id) {
    return groups.get(group).stream().filter(o -> o.active() && o.id().equals(id)).findFirst();
  }
}
