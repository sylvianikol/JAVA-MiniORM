package orm;

import annotations.Column;
import annotations.Entity;
import annotations.Id;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class EntityManager<E> implements DbContext<E> {
    private Connection connection;

    public EntityManager(Connection connection) {
        this.connection = connection;
    }

    @Override
    public <E1> void doCreate(Class<E1> entity) throws SQLException {
        String query = "CREATE TABLE " + this.getTableName(entity) + " (";
        String columns = "";

        Field[] fields = entity.getDeclaredFields();

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            field.setAccessible(true);

            columns += this.getColumnName(field) + " " + this.getDBType(field);

            if (field.isAnnotationPresent(Id.class)) {
                columns += " PRIMARY KEY AUTO_INCREMENT";
            }

            if (i < fields.length - 1) {
                columns += ",\n";
            }
        }

        query += columns + ")";

        connection.prepareStatement(query).execute();
    }

    @Override
    public <E1> void doAlter(Class<E1> entity) throws SQLException {
        String query = "ALTER TABLE " + this.getTableName(entity);
        List<String> toAdd = new ArrayList<>();

        Field[] fields = entity.getDeclaredFields();

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            field.setAccessible(true);

            if (!this.checkIfColumnExists(field, entity)) {
                toAdd.add(" ADD " + this.getColumnName(field) + " " + this.getDBType(field));
            }
        }

        query += String.join(", ", toAdd);

        connection.prepareStatement(query).execute();
    }

    @Override
    public boolean persist(E entity) throws IllegalAccessException, SQLException {
        Field id = this.getId(entity.getClass());
        id.setAccessible(true);
        Object value = id.get(entity);

        if (value == null || (Integer)value <= 0) {
            return this.doInsert(entity, id);
        }

        return this.doUpdate(entity, id);
    }

    private boolean doInsert(E entity, Field primary) throws IllegalAccessException, SQLException {
        if (!this.checkIfTableExists(entity.getClass())) {
            this.doCreate(entity.getClass());
        }

        if (this.checkIfTheresNewColumn(entity.getClass().getDeclaredFields(), entity)) {
            this.doAlter(entity.getClass());
        }

        String query = "INSERT INTO " + this.getTableName(entity.getClass()) + " ";
        String columns = "(";
        String values = "(";

        Field[] fields = entity.getClass().getDeclaredFields();

        for (int i = 0; i < fields.length; i++) {
            fields[i].setAccessible(true);

            if (!fields[i].isAnnotationPresent(Id.class)) {
                columns += this.getColumnName(fields[i]);

                Object value = fields[i].get(entity);

                if (value instanceof Date) {
                    values += "'" + new SimpleDateFormat("yyyy-MM-dd").format(value) + "'";
                } else {
                    values += value instanceof String ? "'" + value + "'" : value;
                }

                if (i < fields.length - 1) {
                    values += ", ";
                    columns += ", ";
                }
            }
        }

        query += columns + ") VALUES " + values + ")";

        return connection.prepareStatement(query).execute();
    }

    private boolean doUpdate(E entity, Field id) throws IllegalAccessException, SQLException {
        String query = "UPDATE " + this.getTableName(entity.getClass()) + " SET ";
        String columnAndValue = "";
        String where = " WHERE " + this.getColumnName(id) + " = " + id.get(entity);
        Field[] fields = entity.getClass().getDeclaredFields();

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            field.setAccessible(true);

            Object value = field.get(entity);

            if (value instanceof Date) {
                columnAndValue += this.getColumnName(field) + " = '" +
                        new SimpleDateFormat("yyyy-MM-dd").format(value) + "'";
            } else if (value instanceof String) {
                columnAndValue += this.getColumnName(field) + " = '" + value + "'";
            } else {
                columnAndValue += this.getColumnName(field) + " = " + value;
            }

            if (i < fields.length - 1) {
                columnAndValue += ", ";
            }
        }

        query += columnAndValue + where;

        return connection.prepareStatement(query).execute();
    }

    @Override
    public Iterable<E> find(Class<E> table) throws InvocationTargetException, SQLException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        return this.find(table, null);
    }

    @Override
    public Iterable<E> find(Class<E> table, String where) throws SQLException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Statement statement = connection.createStatement();
        String query = "SELECT * FROM " + table.getAnnotation(Entity.class).name() +
                " WHERE 1 " + (where != null ? "AND " + where : "");
        ResultSet resultSet = statement.executeQuery(query);

        List<E> entities = new ArrayList<>();

        while (resultSet.next()) {
            E entity = table.getDeclaredConstructor().newInstance();
            this.fillEntity(table, resultSet, entity);
            entities.add(entity);
        }

        return entities;
    }

    @Override
    public E findFirst(Class<E> table) throws InvocationTargetException, SQLException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        return this.findFirst(table, null);
    }

    @Override
    public E findFirst(Class<E> table, String where) throws SQLException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Statement statement = connection.createStatement();
        String query = "SELECT * FROM " + table.getAnnotation(Entity.class).name() +
                " WHERE 1 " + (where != null ? "AND " + where : "") + " LIMIT 1";
        ResultSet resultSet = statement.executeQuery(query);
        E entity = table.getDeclaredConstructor().newInstance();

        resultSet.next();
        this.fillEntity(table, resultSet, entity);
        return entity;
    }

    @Override
    public boolean delete(E entity) throws SQLException, IllegalAccessException {

        String query = "DELETE FROM " + this.getTableName(entity.getClass()) +
                " WHERE id = ";

        Field[] fields = entity.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);

            if (field.isAnnotationPresent(Id.class)) {
                Object value = field.get(entity);
                query += value;
                break;
            }
        }

        return connection.prepareStatement(query).execute();
    }

    private Field getId(Class entity) {
        return Arrays.stream(entity.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Entity does not have primary key"));
    }

    private void fillEntity(Class<E> table, ResultSet resultSet, E entity) throws SQLException, IllegalAccessException {
        Field[] fields = table.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            this.fillField(field, entity, resultSet, this.getColumnName(field));
        }
    }

    private void fillField(Field field, Object instance, ResultSet resultSet, String fieldName) throws SQLException, IllegalAccessException {
        field.setAccessible(true);
        if (field.getType() == int.class || field.getType() == Integer.class) {
            field.set(instance, resultSet.getInt(fieldName));
        } else if (field.getType() == String.class) {
            field.set(instance, resultSet.getString(fieldName));
        } else if (field.getType() == Date.class) {
            field.set(instance, resultSet.getDate(fieldName));
        }
    }

    private String getTableName(Class entity) {
        String tableName = ((Entity)entity.getAnnotation(Entity.class)).name();

        if (tableName.isEmpty()) {
            tableName = entity.getSimpleName();
        }

        return tableName;
    }

    private String getColumnName(Field field) {
        String columnName = field.getAnnotation(Column.class).name();

        if (columnName.isEmpty()) {
            columnName = field.getName();
        }

        return columnName;
    }

    private String getDBType(Field field) {
        String result = "";
        String type = field.getType().getSimpleName();
        switch (type) {
            case "int":
            case "Integer":
                result = "INT";
                break;
            case "String":
                result = "VARCHAR(50)";
                break;
            case "Date":
                result = "DATE";
                break;
            case "double":
            case "Double":
                result = "DECIMAL(10, 2)";
                break;
        }

        return result;
    }

    private boolean checkIfTableExists(Class entity) throws SQLException {
//        String query = "SELECT COUNT(*) AS 'count'" +
//                "FROM information_schema.tables " +
//                "WHERE table_schema = 'ormdb' " +
//                "AND table_name = '" + this.getTableName(entity) + "'";

//        PreparedStatement statement = connection.prepareStatement(query);
//        ResultSet resultSet = statement.executeQuery();
//
//        resultSet.next();
//
//        return resultSet.getInt("count") == 1;

        String query = "SELECT TABLE_NAME " +
                "FROM information_schema.tables " +
                "WHERE table_schema = 'ormdb' " +
                "AND table_name = '" + this.getTableName(entity) + "'";

        ResultSet resultSet = connection.prepareStatement(query).executeQuery();

        return resultSet.next();
    }

    private boolean checkIfColumnExists(Field field, Class entity) throws SQLException {
        String query = "SELECT `COLUMN_NAME`\n" +
                "FROM information_schema.`COLUMNS`\n" +
                "WHERE `TABLE_NAME` = '" + this.getTableName(entity) +
                "' AND `COLUMN_NAME` = '" + this.getColumnName(field) + "'";

        ResultSet resultSet = connection.prepareStatement(query).executeQuery();

        return resultSet.next();
    }

    private boolean checkIfTheresNewColumn(Field[] fields, E entity) throws SQLException {
        for (Field field : fields) {
            if (!this.checkIfColumnExists(field, entity.getClass())) {
                return true;
            }
        }
        return false;
    }
}
