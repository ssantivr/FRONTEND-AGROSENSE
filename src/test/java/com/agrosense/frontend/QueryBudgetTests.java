package com.agrosense.frontend;

import com.agrosense.frontend.repository.SensorRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How many SQL statements each page costs with the sample data (2 estates, 3 crops, 6 sensors). A budget
 * that grows with the number of rows is an N+1 query coming back.
 */
@SpringBootTest
@ActiveProfiles("demo")
class QueryBudgetTests {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private SensorRepository sensorRepository;

    private MockMvc mvc;
    private UserDetails demoUser;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        demoUser = userDetailsService.loadUserByUsername("demo@agrosense.co");
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    void theDashboardCarriesTheDataItsWidgetsWouldOtherwiseRequest() throws Exception {
        mvc.perform(get("/").with(user(demoUser)))
                .andExpect(content().string(containsString("data-markers=\"[{&quot;id&quot;:")))
                .andExpect(content().string(containsString("&quot;latitude&quot;:")))
                .andExpect(content().string(containsString("data-days=\"[{&quot;date&quot;:&quot;")));
    }

    @Test
    void staticFilesAreCachedByTheBrowserAndPagesAreNot() throws Exception {
        mvc.perform(get("/fonts/PlusJakartaSans.woff2"))
                .andExpect(header().string("Cache-Control", "max-age=2592000, public"));
        mvc.perform(get("/img/photos/coffee-960.jpg"))
                .andExpect(header().string("Cache-Control", "max-age=2592000, public"));
        mvc.perform(get("/webjars/chart.js/4.4.1/dist/chart.umd.js"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        // Revalidated on every use, so a new version is never mixed with an old page.
        mvc.perform(get("/js/app.js")).andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/css/styles.css")).andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/login")).andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void pagesStayWithinTheirQueryBudget() throws Exception {
        Integer sensorId = sensorRepository.findByCropEstateUserEmailOrderBySensorCode("demo@agrosense.co").get(0)
                .getIdSensor();
        Map<String, Integer> budgets = new LinkedHashMap<>();
        // Dashboard: estates, sensors, their latest readings, hourly trends, the week's irrigations, open
        // alerts, alerts per estate, alert count.
        budgets.put("/", 8);
        budgets.put("/parcelas", 2);
        // One estate: estates, sensors, their latest readings, its open alerts, its irrigations, alert count.
        budgets.put("/parcelas/1", 6);
        budgets.put("/sensores", 3);
        budgets.put("/riego", 3);
        budgets.put("/alertas", 2);
        // Reports: irrigations, estates, sensors, their latest readings, reading statistics, alert counts,
        // alert count of the layout.
        budgets.put("/reportes", 7);
        budgets.put("/configuracion", 2);
        budgets.put("/api/ui/water-usage", 1);
        // Estates, sensors, their latest readings, open alerts per estate.
        budgets.put("/api/ui/estates/markers", 4);
        budgets.put("/api/ui/sensors/" + sensorId + "/readings", 2);

        for (Map.Entry<String, Integer> budget : budgets.entrySet()) {
            statistics.clear();
            mvc.perform(get(budget.getKey()).with(user(demoUser))).andExpect(status().isOk());
            assertThat(statistics.getPrepareStatementCount()).as("statements for %s", budget.getKey())
                    .isLessThanOrEqualTo(budget.getValue());
        }
    }
}
