package lab.conference;

import java.util.List;
import java.util.Map;
import lab.conference.options.Catalog;
import lab.conference.options.CatalogOption;
import lab.conference.options.ConsentFixture;
import lab.conference.options.GroupId;

/** In-memory catalogs for unit tests. */
public final class TestCatalogs {

  private TestCatalogs() {}

  public static Catalog withConsent(boolean required) {
    return new Catalog(
        "Unit Conference",
        new ConsentFixture("c1", "I agree.", required),
        Map.of(
            GroupId.WORKSHOPS,
            List.of(
                new CatalogOption("ws-a", "Workshop A", true),
                new CatalogOption("ws-off", "Off", false)),
            GroupId.EVENTS,
            List.of(new CatalogOption("ev-a", "Event A", true)),
            GroupId.MEALS,
            List.of(new CatalogOption("meal-a", "Meal A", true)),
            GroupId.OTHER,
            List.of()));
  }

  public static Catalog withoutConsent() {
    return new Catalog(
        "Unit Conference",
        null,
        Map.of(GroupId.WORKSHOPS, List.of(new CatalogOption("ws-a", "Workshop A", true))));
  }
}
