import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 81975 on 2017/8/24.
 */
public class MongoTest {

    private MongoClient mongoClient;

    private MongoDatabase mongoDatabase;

    private MongoCollection<Document> collection;

    @Before
    public void init(){
        mongoClient = new MongoClient("localhost",27017);
        mongoDatabase = mongoClient.getDatabase("TestDB");
        System.err.println("Connect to database successfully");
        collection = mongoDatabase.getCollection("testList");
        System.err.println("Get Collection Successfully");
    }

    @Test
    public void insert(){
       Document document = new Document().
               append("title", "MongoDB").
               append("description", "database").
               append("likes", 100).
               append("by", "Fly");
        System.err.println(document.toJson());
        /*List<Document> documents = new ArrayList<Document>();
        documents.add(document);
        collection.insertMany(documents);
        System.out.print("Insert Success");*/
    }

    @Test
    public void find(){
        FindIterable<Document> findIterable = collection.find();
        MongoCursor<Document> mongoCursor = findIterable.iterator();
        while (mongoCursor.hasNext()){
            System.err.println(mongoCursor.next());
        }
    }

    @Test
    public void update(){
        UpdateResult updateResult =  collection.updateMany(Filters.eq("likes",100),new Document("$set",new Document("likes",200)));
        System.err.println(updateResult.getMatchedCount());
    }

    @Test
    public void del(){
        DeleteResult  deleteResult= collection.deleteOne(Filters.eq("likes",200));
        DeleteResult deleteResultMany=collection.deleteMany(Filters.eq("by","菜鸟教程"));
        System.err.println("DeleteOne:"+deleteResult.getDeletedCount());
        System.err.println("DeleteMany:"+deleteResultMany.getDeletedCount());
    }


}
