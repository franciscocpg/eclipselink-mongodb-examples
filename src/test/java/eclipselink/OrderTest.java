package eclipselink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.eclipse.persistence.internal.nosql.adapters.mongo.MongoConnection;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.mongodb.DB;
import java.util.logging.Logger;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

/**
 * Tests for EclpiseLink for MongoDB.
 *
 * @author Tobias Trelle, codecentric AG.
 */
public class OrderTest {

    /**
     * entity manager.
     */
    private static EntityManager em;
    /**
     * TX - with MongoDB ?!?
     */
    private EntityTransaction tx;
    private String id;
    private static final Logger LOG = Logger.getLogger(OrderTest.class.getName());

    @BeforeClass
    public static void setUpPU() {
        em = Persistence.createEntityManagerFactory("mongodb").createEntityManager();
    }

    /**
     * Attention: EclipseLink requires an active transaction although MongoDB
     * itself
     * <b>DOES NOT</b> transaction at all!
     */
    @Before
    public void setUp() {
        // is needed by JPA/EntityManager
        tx = em.getTransaction();
        tx.begin();

        // given ...
        DB db = ((MongoConnection) em.unwrap(javax.resource.cci.Connection.class)).getDB();
        db.dropDatabase();
        Order order = new Order("Tobias Trelle");
        List<Item> items = new ArrayList<Item>();
        items.add(new Item(1, 47.11, "Item #1"));
        items.add(new Item(2, 42.0, "Item #2"));
        order.setItems(items);
        em.persist(order);
        em.flush();
        id = order.getId();
        LOG.severe("id:" + id);
    }

    /**
     * Uses entity manager primary key lookup.
     */
    @Test
    public void should_find_by_primary_key() {
        // when
        Order order = em.find(Order.class, id);

        // then
        assertOrder(order);
    }

    /**
     * Uses JPQL query (that gets translated to native MongoDB query.
     */
    @Test
    public void should_find_by_items_quantity() {
        // when
        Order order = em
                .createQuery("SELECT o FROM Order o JOIN o.items i WHERE i.quantity = 2", Order.class)
                .getSingleResult();

        // then
        assertOrder(order);
    }

    /**
     * Uses native MongoDB query (which consists of the full find command like
     * used in the Mongo shell, not only the query string itself.
     * <p>
     * Note that EclipseLink converts the names of collections field to upper
     * case.
     */
    @Test
    public void should_find_by_primary_with_native_query() {
        // when
        Order order = (Order) em
                .createNativeQuery("db.ORDER.findOne({_id: \"" + id + "\"})", Order.class)
                .getSingleResult();

        // then
        assertOrder(order);
    }

    @Test
    public void should_find_by_primary_with_criteria() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Order> cq = cb.createQuery(Order.class);
        Root<Order> pet = cq.from(Order.class);
        cq.select(pet);
        TypedQuery<Order> q = em.createQuery(cq);
        Order order = q.getSingleResult();

        // then
        assertOrder(order);
    }

    /**
     * Native queries with nested paths (in this example "ITEMS.QUANTITY") do
     * not seem to work properly, they raise an error.
     */
    @Ignore
    public void should_find_by_items_quantity_with_native_query() {
        // when
        Order order = (Order) em
                .createNativeQuery("db.ORDER.findOne({\"ITEMS.QUANTITY\": 2})", Order.class)
                .getSingleResult();

        // then
        assertOrder(order);
    }

    @After
    public void tearDown() {
        tx.commit();
    }

    @AfterClass
    public static void closeEntityManager() {
        if (em != null) {
            em.close();
        }
    }

    private static void assertOrder(Order order) {
        assertNotNull(order);
        assertEquals(2, order.getItems().size());
    }
}
