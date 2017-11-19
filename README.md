##### Adapt a [Retrofit](http://square.github.io/retrofit/) call to support request's priority

##### Usage:

Add `PrioritizedCallAdapterFactory` to the retrofit instance, then 

```java
  public interface ExampleService {
      @Priority(Priorities.HIGH) // add priority annotation, that's it
      @GET("/")
      Call<String> getExamples();
  }
```

If the return type is not `Call`, use `PrioritizedCallFactory` to create a new Call instance that 
support request priority





