##### Adapt a [Retrofit](http://square.github.io/retrofit/) call to support request's priority

##### Usage:

```java
  public interface ExampleService {
      @Priority(Priorities.HIGH) // add priority annotation, that's it
      @GET("/")
      Call<String> getExamples();
  }
```





