Application Analytics

### Setup
* Run the following to create the database schema.

```
KAIAO_DB_URL='jdbc:postgresql://host:port/the-kaiao-db' KAIAO_DB_USER='the-kaiao-user' KAIAO_DB_PASSWORD='<password-here>'' bb db:migrate!
```

* Run the following to create a project.

```
KAIAO_DB_URL='jdbc:postgresql://host:port/the-kaiao-db' KAIAO_DB_USER='the-kaiao-user' KAIAO_DB_PASSWORD='<password-here>'' bb kaiao:create-project! :name '"My Site Name"' :domain '"my.site.com"'
```
