package foo;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

@Api(
    name = "myApi",
    version = "v1",
    audiences = {"your-client-id"},
    clientIds = {"your-client-id"},
    namespace = @ApiNamespace(
        ownerDomain = "example.com",
        ownerName = "example.com",
        packagePath = "")
)
public class PetitionEndpoint {
    private DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    private static final Logger log = Logger.getLogger(PetitionEndpoint.class.getName());

    @ApiMethod(name = "createPetition", path = "createPetition", httpMethod = ApiMethod.HttpMethod.POST)
    public Entity createPetition(Petition petition) {
        Entity petitionEntity = new Entity("Petition");
        petitionEntity.setProperty("title", petition.getTitle());
        petitionEntity.setProperty("owner", petition.getOwner());
        petitionEntity.setProperty("description", petition.getDescription());
        petitionEntity.setProperty("createdAt", new Date());
        petitionEntity.setProperty("tags", new ArrayList<String>());

        datastore.put(petitionEntity);
        petition.setId(petitionEntity.getKey().getId());

        return petitionEntity;
    }

    @ApiMethod(name = "signPetition", httpMethod = ApiMethod.HttpMethod.POST)
    public Entity signPetition(SignatureRequest signatureRequest) throws Exception {
        // Check if user already signed the petition
        Long petitionId = signatureRequest.petitionId != null ? signatureRequest.petitionId : 666;
        String userId = signatureRequest.userId;
        Query query = new Query("SignaturesPetition")
                .setFilter(new Query.CompositeFilter(Query.CompositeFilterOperator.AND, List.of(
                        new Query.FilterPredicate("petitionId", Query.FilterOperator.EQUAL, petitionId),
                        new Query.FilterPredicate("signatures", Query.FilterOperator.EQUAL, userId)
                )));
        List<Entity> results = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(1));
        if (!results.isEmpty()) {
            throw new Exception("User has already signed this petition");
        }

        // Find a non-full signatures entity
        query = new Query("SignaturesPetition")
                .setFilter(new Query.FilterPredicate("petitionId", Query.FilterOperator.EQUAL, petitionId))
                .addSort("createdAt", Query.SortDirection.DESCENDING);
        results = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(1));

        Entity signatureEntity;
        if (results.isEmpty() || (boolean) results.get(0).getProperty("isFull")) {
            // Create a new signatures entity
            signatureEntity = new Entity("SignaturesPetition");
            signatureEntity.setProperty("petitionId", petitionId);
            signatureEntity.setProperty("signatures", new ArrayList<String>());
            signatureEntity.setProperty("isFull", false);
        } else {
            signatureEntity = results.get(0);
        }

        List<String> signatures = (List<String>) signatureEntity.getProperty("signatures");
        signatures.add(userId);
        if (signatures.size() >= SignaturesPetition.MAX_SIGNATURES) {
            signatureEntity.setProperty("isFull", true);
        }

        signatureEntity.setProperty("signatures", signatures);

        Transaction txn = datastore.beginTransaction();
        datastore.put(signatureEntity);
        txn.commit();

        return signatureEntity;
    }

    @ApiMethod(name = "myPetitions", httpMethod = ApiMethod.HttpMethod.GET)
    public CollectionResponse<Entity> myPetitions(@Nullable @Named("next") String cursorString) {
        Query q = new Query("Petition").setFilter(new FilterPredicate("owner", FilterOperator.EQUAL, "KIWIZ"));

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        PreparedQuery pq = datastore.prepare(q);

        FetchOptions fetchOptions = FetchOptions.Builder.withLimit(2);

        if (cursorString != null) {
            fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
        }

        QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);
        cursorString = results.getCursor().toWebSafeString();

        return CollectionResponse.<Entity>builder().setItems(results).setNextPageToken(cursorString).build();
    }

    @ApiMethod(name = "petitions", httpMethod = ApiMethod.HttpMethod.GET)
    public CollectionResponse<Entity> petitions(@Nullable @Named("next") String cursorString) {
        Query q = new Query("Petition");

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        PreparedQuery pq = datastore.prepare(q);

        FetchOptions fetchOptions = FetchOptions.Builder.withLimit(2);

        if (cursorString != null) {
            fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
        }

        QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);
        cursorString = results.getCursor().toWebSafeString();

        return CollectionResponse.<Entity>builder().setItems(results).setNextPageToken(cursorString).build();
    }

    @ApiMethod(name = "addTag", path = "addTag", httpMethod = ApiMethod.HttpMethod.POST)
public Entity addTag(TagRequest tagRequest) throws Exception {
    Long petitionId = tagRequest.getPetitionId();
    String tag = tagRequest.getTag();

    if (petitionId == null || tag == null || tag.isEmpty()) {
        throw new IllegalArgumentException("petitionId and tag are required");
    }

    Key petitionKey = KeyFactory.createKey("Petition", petitionId);
    Entity petitionEntity = datastore.get(petitionKey);

    List<String> tags = (List<String>) petitionEntity.getProperty("tags");
    if (tags == null) {
        tags = new ArrayList<>();
    }
    tags.add(tag);
    petitionEntity.setProperty("tags", tags);

    datastore.put(petitionEntity);

    return petitionEntity;
}

@ApiMethod(name = "findPetitionsByTag", path = "findPetitionsByTag", httpMethod = ApiMethod.HttpMethod.GET)
public List<Petition> findPetitionsByTag(@Named("tag") String tag) {
    Query query = new Query("Petition")
            .setFilter(new Query.FilterPredicate("tags", Query.FilterOperator.EQUAL, tag))
            .addSort("createdAt", Query.SortDirection.DESCENDING);

    List<Entity> petitionEntities = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

    List<Petition> petitions = new ArrayList<>();
    for (Entity entity : petitionEntities) {
        Petition petition = new Petition();
        petition.setId(entity.getKey().getId());
        petition.setTitle((String) entity.getProperty("title"));
        petition.setOwner((String) entity.getProperty("owner"));
        petition.setDescription((String) entity.getProperty("description"));
        petition.setCreatedAt((Date) entity.getProperty("createdAt"));
        petition.setTags((List<String>) entity.getProperty("tags"));
        petitions.add(petition);
    }

    return petitions;
}

    

    @ApiMethod(name = "listSignedPetitions", httpMethod = ApiMethod.HttpMethod.GET)
    public List<Entity> listSignedPetitions(@Named("userId") String userId) {
        Query query = new Query("SignaturesPetition")
                .setFilter(new Query.FilterPredicate("signatures", Query.FilterOperator.EQUAL, userId));

        List<Entity> signatureEntities = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

        List<Entity> petitions = new ArrayList<>();
        for (Entity signature : signatureEntities) {
            Long petitionId = (Long) signature.getProperty("petitionId");
            Query petitionQuery = new Query("Petition").setFilter(new Query.FilterPredicate("__key__", Query.FilterOperator.EQUAL, KeyFactory.createKey("Petition", petitionId)));
            Entity petition = datastore.prepare(petitionQuery).asSingleEntity();
            if (petition != null) {
                petitions.add(petition);
            }
        }

        return petitions;
    }

    @ApiMethod(name = "getTop100Petitions", path = "getTop100Petitions", httpMethod = ApiMethod.HttpMethod.GET)
public List<Petition> getTop100Petitions() {
    Query query = new Query("Petition")
            .addSort("createdAt", Query.SortDirection.DESCENDING);

    List<Entity> petitionEntities = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(100));

    List<Petition> petitions = new ArrayList<>();
    for (Entity entity : petitionEntities) {
        Petition petition = new Petition();
        petition.setId(entity.getKey().getId());
        petition.setTitle((String) entity.getProperty("title"));
        petition.setOwner((String) entity.getProperty("owner"));
        petition.setDescription((String) entity.getProperty("description"));
        petition.setCreatedAt((Date) entity.getProperty("createdAt"));
        petition.setTags((List<String>) entity.getProperty("tags"));
        petitions.add(petition);
    }

    return petitions;
}

@ApiMethod(name = "getSignatures", path = "getSignatures", httpMethod = ApiMethod.HttpMethod.GET)
public List<String> getSignatures(@Named("petitionId") Long petitionId) {
    Query query = new Query("SignaturesPetition")
            .setFilter(new Query.FilterPredicate("petitionId", Query.FilterOperator.EQUAL, petitionId))
            .addSort("createdAt", Query.SortDirection.DESCENDING);

    List<Entity> signatureEntities = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

    List<String> signatures = new ArrayList<>();
    for (Entity entity : signatureEntities) {
        List<String> entitySignatures = (List<String>) entity.getProperty("signatures");
        if (entitySignatures != null) {
            signatures.addAll(entitySignatures);
        }
    }

    return signatures;
}


}
