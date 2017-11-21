##### Adapt a [Retrofit](http://square.github.io/retrofit/) call to support request's priority

##### Example:

```java
    private void initRetrofit() {
        Retrofit.Builder()
               .baseUrl(BASE_URL)
               .addCallAdapterFactory(PrioritizedCallAdapterFactory.create()) // <-- add calladapter factory
               .addConverterFactory(MoshiConverterFactory.create())
               .build();
    }
  
    public interface ExampleService {
        @Priority(Priorities.HIGH) // <-- add priority annotation, that's it
        @GET("/")
        Call<String> getExamples();
    }
```

If the return type is not `Call`, use `PrioritizedCallFactory` to create a new Call instance that 
support request priority


##### Gradle
```
    compile 'com.wenhui:prioritizeretrofitadapter:1.0.0'

```





