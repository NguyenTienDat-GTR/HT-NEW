package phucuong.ht.common.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import phucuong.ht.common.model.BaseModel;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public abstract class BaseRepo<T extends BaseModel, ID> {

    private static final List<String> IGNORED_PROPERTIES = List.of(
            "class",
            "createdAt",
            "createdBy",
            "updatedAt",
            "updatedBy",
            "status"
    );

    protected final EntityManager entityManager;
    protected final Class<T> entityClass;
    private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

    protected BaseRepo(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
    }

    /**
     * Creates a new entity instance and copies request data into it.
     * Null audit fields and status are managed centrally here so modules do not
     * need to repeat create logic in their own repositories.
     */
    @Transactional
    public <R> T create(R request) {
        return create(request, null);
    }

    @Transactional
    public <R> T create(R request, String actor) {
        T entity = newEntity();
        applyRequest(entity, request, true);

        OffsetDateTime now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (StringUtils.hasText(actor)) {
            entity.setCreatedBy(actor);
            entity.setUpdatedBy(actor);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(Boolean.TRUE);
        }

        entityManager.persist(entity);
        return entity;
    }

    /**
     * Loads the target entity, applies non-null request values, and updates the audit timestamp.
     * This method is intentionally generic so modules can keep only request validation.
     */
    @Transactional
    public <R> T update(ID id, R request) {
        return update(id, request, null);
    }

    @Transactional
    public <R> T update(ID id, R request, String actor) {
        T entity = getRequired(id);
        applyRequest(entity, request, false);
        entity.setUpdatedAt(OffsetDateTime.now());
        if (StringUtils.hasText(actor)) {
            entity.setUpdatedBy(actor);
        }
        return entity;
    }

    /**
     * Reads one entity by primary key and returns an Optional instead of throwing.
     */
    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(entityManager.find(entityClass, id));
    }

    @Transactional(readOnly = true)
    public Optional<T> findOneByField(String fieldName, Object value) {
        if (!StringUtils.hasText(fieldName) || value == null) {
            return Optional.empty();
        }

        String normalizedField = normalizeFieldPath(fieldName);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        Path<?> path = resolvePath(root, normalizedField);

        query.select(root).where(cb.equal(path, convertValue(value, path.getJavaType())));
        return findFirst(entityManager.createQuery(query).setMaxResults(1).getResultList());
    }

    @Transactional(readOnly = true)
    public boolean existsByField(String fieldName, Object value) {
        return findOneByField(fieldName, value).isPresent();
    }

    /**
     * Reads one entity by primary key and throws when the row does not exist.
     */
    @Transactional(readOnly = true)
    public T getRequired(ID id) {
        return findById(id).orElseThrow(() -> new EntityNotFoundException(entityClass.getSimpleName() + " not found: " + id));
    }

    /**
     * Returns a paged result set using dynamic search, filter, status, and sort criteria.
     * Search fields and filter keys are resolved at runtime from the request payload.
     */
    @Transactional(readOnly = true)
    public Page<T> findAll(BaseSearchRequest request) {
        BaseSearchRequest criteria = request == null ? new BaseSearchRequest() : request;
        Pageable pageable = PageRequest.of(Math.max(criteria.getPage(), 0), Math.max(criteria.getSize(), 1));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> dataQuery = cb.createQuery(entityClass);
        Root<T> dataRoot = dataQuery.from(entityClass);
        List<Predicate> predicates = buildPredicates(criteria, cb, dataRoot);

        dataQuery.select(dataRoot).where(predicates.toArray(Predicate[]::new));
        applySort(criteria, cb, dataQuery, dataRoot);

        TypedQuery<T> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityClass);
        countQuery.select(cb.countDistinct(countRoot)).where(buildPredicates(criteria, cb, countRoot).toArray(Predicate[]::new));

        List<T> content = typedQuery.getResultList();
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Hard-deletes the row after loading it. If later we need soft delete, this is
     * the single method to replace without touching callers.
     */
    @Transactional
    public void delete(ID id) {
        T entity = getRequired(id);
        entityManager.remove(entityManager.contains(entity) ? entity : entityManager.merge(entity));
    }

    /**
     * Flips status between active and inactive by negating the current Boolean value.
     */
    @Transactional
    public T toggleStatus(ID id) {
        T entity = getRequired(id);
        entity.setStatus(!Boolean.TRUE.equals(entity.getStatus()));
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }

    /**
     * Copies request properties onto the entity.
     * - regular fields are written directly
     * - relation id fields are resolved to managed references
     * - base audit/status/id fields are ignored
     */
    protected void applyRequest(T entity, Object request, boolean allowNulls) {
        if (request == null) {
            return;
        }

        BeanWrapper source = new BeanWrapperImpl(request);
        BeanWrapper target = new BeanWrapperImpl(entity);

        for (PropertyDescriptor property : source.getPropertyDescriptors()) {
            String propertyName = property.getName();
            if (IGNORED_PROPERTIES.contains(propertyName) || propertyName.equals(getIdPropertyName(entityClass))) {
                continue;
            }

            Object value = source.getPropertyValue(propertyName);
            if (value == null && !allowNulls) {
                continue;
            }

            if (target.isWritableProperty(propertyName)) {
                target.setPropertyValue(propertyName, convertValue(value, target.getPropertyType(propertyName)));
                continue;
            }

            applyRelationById(entity, propertyName, value);
        }
    }

    /**
     * Builds predicates for status, dynamic filters, and free-text search.
     * Both search and filter keys may target nested property paths.
     */
    private List<Predicate> buildPredicates(BaseSearchRequest request, CriteriaBuilder cb, Root<T> root) {
        List<Predicate> predicates = new ArrayList<>();

        if (request.getStatus() != null) {
            predicates.add(cb.equal(root.get("status"), request.getStatus()));
        }

        Map<String, Object> filters = request.getFilters() == null ? Map.of() : request.getFilters();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String fieldPath = normalizeFieldPath(entry.getKey());
            Object filterValue = entry.getValue();
            if (!StringUtils.hasText(fieldPath) || filterValue == null) {
                continue;
            }

            Path<?> path = resolvePath(root, fieldPath);
            if (filterValue instanceof Collection<?> collection) {
                List<Object> converted = new ArrayList<>();
                for (Object item : collection) {
                    converted.add(convertValue(item, path.getJavaType()));
                }
                predicates.add(path.in(converted));
                continue;
            }

            predicates.add(cb.equal(path, convertValue(filterValue, path.getJavaType())));
        }

        if (StringUtils.hasText(request.getSearch()) && request.getSearchFields() != null && !request.getSearchFields().isEmpty()) {
            String keyword = "%" + request.getSearch().toLowerCase() + "%";
            List<Predicate> searchPredicates = new ArrayList<>();
            for (String field : request.getSearchFields()) {
                field = normalizeFieldPath(field);
                if (!StringUtils.hasText(field)) {
                    continue;
                }
                Expression<String> expression = cb.lower(resolvePath(root, field).as(String.class));
                searchPredicates.add(cb.like(expression, keyword));
            }
            if (!searchPredicates.isEmpty()) {
                predicates.add(cb.or(searchPredicates.toArray(Predicate[]::new)));
            }
        }

        return predicates;
    }

    /**
     * Applies runtime sorting if the request supplies a sort field.
     */
    private void applySort(BaseSearchRequest request, CriteriaBuilder cb, CriteriaQuery<T> query, Root<T> root) {
        String sortBy = normalizeFieldPath(request.getSortBy());
        if (!StringUtils.hasText(sortBy)) {
            return;
        }

        Path<?> path = resolvePath(root, sortBy);
        if ("DESC".equalsIgnoreCase(request.getSortDirection())) {
            query.orderBy(cb.desc(path));
            return;
        }
        query.orderBy(cb.asc(path));
    }

    /**
     * Resolves a dotted path like `leader.parish.name` into a Criteria API path.
     */
    private Path<?> resolvePath(Path<?> root, String fieldPath) {
        Path<?> current = root;
        for (String field : fieldPath.split("\\.")) {
            current = current.get(field);
        }
        return current;
    }

    /**
     * Normalizes field paths coming from query-string style map keys such as
     * ["name"], ['name'], [name], or ordinary dotted paths.
     */
    private String normalizeFieldPath(String fieldPath) {
        if (!StringUtils.hasText(fieldPath)) {
            return fieldPath;
        }

        String normalized = fieldPath.trim();
        if (normalized.startsWith("[")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("]")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        return normalized.trim();
    }

    /**
     * Matches a request field like `leaderCode` to a relation field on the entity
     * and replaces the id with a managed reference.
     */
    private void applyRelationById(T entity, String requestProperty, Object requestValue) {
        if (requestValue == null) {
            return;
        }

        BeanWrapper target = new BeanWrapperImpl(entity);
        for (Field field : getAllFields(entityClass)) {
            if (!isRelationField(field)) {
                continue;
            }

            String relationIdProperty = getIdPropertyName(field.getType());
            if (relationIdProperty.equals(requestProperty)) {
                Object convertedId = convertValue(requestValue, getIdPropertyType(field.getType()));
                Object reference = entityManager.getReference(field.getType(), convertedId);
                target.setPropertyValue(field.getName(), reference);
                return;
            }

            if (hasPersistentField(field.getType(), requestProperty)) {
                Object relationEntity = findRelationByField(field.getType(), requestProperty, requestValue);
                target.setPropertyValue(field.getName(), relationEntity);
                return;
            }
        }
    }

    /**
     * Detects whether a field is a relation that should be resolved by id.
     */
    private boolean isRelationField(Field field) {
        return field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class);
    }

    private boolean hasPersistentField(Class<?> type, String fieldName) {
        return getAllFields(type).stream().anyMatch(field -> field.getName().equals(fieldName));
    }

    private Object findRelationByField(Class<?> relationType, String fieldName, Object value) {
        Class<?> fieldType = getAllFields(relationType).stream()
                .filter(field -> field.getName().equals(fieldName))
                .findFirst()
                .map(Field::getType)
                .orElseThrow(() -> new IllegalStateException(relationType.getSimpleName() + " does not define field " + fieldName));

        return entityManager.createQuery(
                        "select entity from " + relationType.getSimpleName() + " entity where entity." + fieldName + " = :value",
                        relationType
                )
                .setParameter("value", convertValue(value, fieldType))
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(relationType.getSimpleName() + " not found by " + fieldName + ": " + value));
    }

    private <E> Optional<E> findFirst(List<E> results) {
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }

    /**
     * Converts DTO values to the target Java type when the caller sends strings,
     * enums, numbers, or other compatible types from JSON.
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null || targetType == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        return conversionService.convert(value, targetType);
    }

    /**
     * Creates a fresh entity instance using the no-args constructor.
     */
    private T newEntity() {
        try {
            return entityClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(entityClass.getSimpleName() + " must expose a no-args constructor", exception);
        }
    }

    /**
     * Reads the name of the entity identifier property through the JPA metamodel.
     */
    private String getIdPropertyName(Class<?> type) {
        EntityType<?> entityType = entityManager.getMetamodel().entity(type);
        return entityType.getSingularAttributes().stream()
                .filter(SingularAttribute::isId)
                .findFirst()
                .map(SingularAttribute::getName)
                .orElseThrow(() -> new IllegalStateException(type.getSimpleName() + " does not define an @Id field"));
    }

    /**
     * Reads the Java type of the entity identifier property through the JPA metamodel.
     */
    private Class<?> getIdPropertyType(Class<?> type) {
        EntityType<?> entityType = entityManager.getMetamodel().entity(type);
        return entityType.getSingularAttributes().stream()
                .filter(SingularAttribute::isId)
                .findFirst()
                .map(SingularAttribute::getJavaType)
                .orElseThrow(() -> new IllegalStateException(type.getSimpleName() + " does not define an @Id field"));
    }

    /**
     * Collects all declared fields from the entity class and its superclasses.
     */
    private List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field[] declaredFields = current.getDeclaredFields();
            fields.addAll(List.of(declaredFields));
            current = current.getSuperclass();
        }
        return fields;
    }
}
