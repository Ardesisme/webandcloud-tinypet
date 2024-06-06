package petition;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.config.Nullable;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.api.server.spi.auth.EspAuthenticator;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;

import foo.PostMessage;

import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;

@Api(name = "tinypet",
     version = "v1",
     audiences = "927375242383-t21v9ml38tkh2pr30m4hqiflkl3jfohl.apps.googleusercontent.com",
  	 clientIds = {"927375242383-t21v9ml38tkh2pr30m4hqiflkl3jfohl.apps.googleusercontent.com",
        "927375242383-jm45ei76rdsfv7tmjv58tcsjjpvgkdje.apps.googleusercontent.com"},
     namespace =
     @ApiNamespace(
		   ownerDomain = "tinypet.example.com",
		   ownerName = "tinypet.example.com",
		   packagePath = "")
     )
public class PetitionEndpoint {
    
	// Random r = new Random();

    // // remember: return Primitives and enums are not allowed. 
	// @ApiMethod(name = "getRandom", httpMethod = HttpMethod.GET)
	// public RandomResult random() {
	// 	return new RandomResult(r.nextInt(6) + 1);
	// }

	// @ApiMethod(name = "hello", httpMethod = HttpMethod.GET)
	// public User Hello(User user) throws UnauthorizedException {
    //     if (user == null) {
	// 		throw new UnauthorizedException("Invalid credentials");
	// 	}
    //     System.out.println("Yeah:"+user.toString());
	// 	return user;
	// }

    // créer une pétition
    @ApiMethod(name="creer_petition", httpMethod=HttpMethod.POST)
    public Entity creerPetition(PostMessage pm){
        Entity e = new Entity("Petition");
        e.setProperty("creator", pm.owner);
        e.setProperty("title", pm.title);
        e.setProperty("creationDate", new Date());
        ArrayList<String> signatures = new ArrayList<>();
        e.setProperty("signatures", signatures);
        ArrayList<String> tags = new ArrayList<>();
        e.setProperty("tags", tags);

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Transaction transaction = datastore.beginTransaction();
        datastore.put(e);
        transaction.commit();
        return e;
    }

    // voir des pétitions (top 100 pétitions triées par date) 
    @ApiMethod(name="petitions", httpMethod=HttpMethod.GET)
    public List<Entity> petitions(){
        // KIWIZ implémenter le curseur
        Query query = new Query("Petition").addSort("creationDate", SortDirection.DESCENDING);

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        PreparedQuery preparedQuery = datastore.prepare(query);
		List<Entity> result = preparedQuery.asList(FetchOptions.Builder.withLimit(100));
		return result;
    }

    // voir ses pétitions
    @ApiMethod(name="mes_petitions", httpMethod=HttpMethod.GET)
    public List<Entity> mesPetitions(@Named("id") String id){
        Query q = new Query("Petition").setFilter(new FilterPredicate("creator", FilterOperator.EQUAL, id)).addSort("creationDate",
        SortDirection.DESCENDING);

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withLimit(10));
        return result;
    }

    // voir les pétitions avec un tag particulier
    @ApiMethod(name="petition_par_tag", httpMethod=HttpMethod.GET)
    public List<Entity> petitionParTag(@Named("tag") String tag){
        Query q = new Query("Petition").setFilter(new FilterPredicate("tags", FilterOperator.EQUAL, tag)).addSort("creationDate",
        SortDirection.DESCENDING);

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withLimit(10));
        return result;
    }

	@ApiMethod(name = "ajouterTag", httpMethod = HttpMethod.POST)
	public Entity ajouterTag(@Named("petitionId") Long petitionId, @Named("tag") String tag, @Named("user") String user) throws NotFoundException, UnauthorizedException {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Key petitionKey = KeyFactory.createKey("Petition", petitionId);

		try {
			Entity petition = datastore.get(petitionKey);
			String creator = (String) petition.getProperty("creator");

			if (!creator.equals(user)) {
				throw new UnauthorizedException("Vous n'êtes pas le créateur de cette pétition.");
			}

			@SuppressWarnings("unchecked")
			List<String> tags = (List<String>) petition.getProperty("tags");
			if (tags == null) {
				tags = new ArrayList<>();
			}

			if (!tags.contains(tag)) {
				tags.add(tag);
				petition.setProperty("tags", tags);

				Transaction txn = datastore.beginTransaction();
				try {
					datastore.put(petition);
					txn.commit();
				} finally {
					if (txn.isActive()) {
						txn.rollback();
					}
				}
			}

			return petition;
		} catch (EntityNotFoundException e) {
			throw new NotFoundException("Pétition non trouvée.");
		}
	}

    
	@ApiMethod(name = "signedpetition", httpMethod = HttpMethod.GET)
	public CollectionResponse<Entity> signedpetition(@Named("petid") String petid, @Nullable @Named("next") String cursorString) {

	    Query q = new Query("D2User").setFilter(new FilterPredicate("signed", FilterOperator.EQUAL, petid));
	    
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
    

	@ApiMethod(name = "getPost",
		   httpMethod = ApiMethod.HttpMethod.GET)
	public CollectionResponse<Entity> getPost(User user, @Nullable @Named("next") String cursorString)
			throws UnauthorizedException {

		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}

		Query q = new Query("Post").
		    setFilter(new FilterPredicate("owner", FilterOperator.EQUAL, user.getEmail()));

		// Multiple projection require a composite index
		// owner is automatically projected...
		// q.addProjection(new PropertyProjection("body", String.class));
		// q.addProjection(new PropertyProjection("date", java.util.Date.class));
		// q.addProjection(new PropertyProjection("likec", Integer.class));
		// q.addProjection(new PropertyProjection("url", String.class));

		// looks like a good idea but...
		// require a composite index
		// - kind: Post
		//  properties:
		//  - name: owner
		//  - name: date
		//    direction: desc

		// q.addSort("date", SortDirection.DESCENDING);

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

	@ApiMethod(name = "postMsg", httpMethod = HttpMethod.POST)
	public Entity postMsg(User user, PostMessage pm) throws UnauthorizedException {

		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}

		Entity e = new Entity("Post", Long.MAX_VALUE-(new Date()).getTime()+":"+user.getEmail());
		e.setProperty("owner", user.getEmail());
		e.setProperty("url", pm.url);
		e.setProperty("body", pm.body);
		e.setProperty("likec", 0);
		e.setProperty("date", new Date());

///		Solution pour pas projeter les listes
//		Entity pi = new Entity("PostIndex", e.getKey());
//		HashSet<String> rec=new HashSet<String>();
//		pi.setProperty("receivers",rec);
		
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Transaction txn = datastore.beginTransaction();
		datastore.put(e);
		txn.commit();
		return e;
	}
}
