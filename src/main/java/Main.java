import entities.User;
import orm.Connector;
import orm.EntityManager;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) throws SQLException, IllegalAccessException, NoSuchMethodException, InstantiationException, InvocationTargetException {

        String username = "root";
        String password = "";

        Connector.createConnection(username, password, "ormdb");
        EntityManager<User> entityManager = new EntityManager<>(Connector.getConnection());

        User user = entityManager.findFirst(User.class, "username = 'Petkan'");
        entityManager.delete(user);

        System.out.printf("User %s is deleted", user.getUsername());

//        User user = new User("Ivan", "dsdf3r3", 35, new Date(), 4555);
//        entityManager.persist(user);

//        User user2 = new User("Pesho", "jyds56dd", 55, new Date());
//        entityManager.persist(user2);

//        User user2 = entityManager.findFirst(User.class, " age = 95");
//        System.out.println(user.getUsername() + " - " + user.getAge());

//        List<User> users = (List<User>) entityManager.find(User.class, " age > 18");
//
//        for (User u : users) {
//            System.out.println(u.getUsername() + " - " + u.getAge());
//        }
//
//        System.out.println("-----------------------------");
//
//        List<User> allUsers = (List<User>) entityManager.find(User.class);
//
//        for (User u : allUsers) {
//            System.out.println(u.getUsername() + " - " + u.getAge());
//        }
    }
}
