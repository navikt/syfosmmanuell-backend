[![Build status](https://github.com/navikt/syfosmmanuell-backend/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmmanuell-backend/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# SYFO Manuell Backend
This project contains the backend for handling manual sykmelding, front end kode: https://github.com/navikt/syfosmmanuell

## Technologies used
* Kotlin
* Ktor
* Gradle
* Junit
* Jackson
* Postgres

## Requirements
* JDK 17

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosmmanuell-backend .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 syfosmmanuell-backend`

### Access to the Postgres database

For utfyllende dokumentasjon se [Postgres i NAV](https://github.com/navikt/utvikling/blob/master/PostgreSQL.md)

#### Tldr

The application uses dynamically generated user / passwords for the database.
To connect to the database one must generate user / password (which lasts for one hour)
as follows:

Use The Vault Browser CLI that is build in https://vault.adeo.no


Preprod credentials:

```
read postgresql/preprod-fss/creds/syfosmmanuell-backend-admin

```

Prod credentials:

```
read postgresql/prod-fss/creds/syfosmmanuell-backend-readonly

```

## Testing the whole flow for handling manual sykmelding in preprod
### Submitting sykmelding:
1. Navigate to syfomock: https://syfomock.dev-sbs.nais.io/ (Remove the "Simple mode" checkbox)
2. Note the number below Msgid (this changes with each submission)
3. Fill in the content you want in the sykmelding
4. Submit the sick leave

### Verification in the sykmelding applications:
1. Log in at https://logs.adeo.no and use the following search string: x_msgId: $ yourMsgId, ex: x_msgId: 58e1d88d-36fa-4756-a06a-32c384ba885f
2. Verify that what you expect to happen with a sykmelding actually happens. It should then be Ok | Manual processing | rejected
   What you look for are items: status = OK, status = MANUAL_PROCESSING or status = INVALID
3. Your message should get the status = MANUAL_PROCESSING  

### Verification in Gosys:
1. Login User (Case managers / supervisors):
   Z992389
2. Check that the sykmelding is placed in gosys:
   - Log in at https://gosys-nais-q1.nais.preprod.local/gosys
   - Search for user with fnr
3. Verify that there is a sykmelding task under tasks overview and 
   that this is the sykmelding you submitted
4. Click on the "Start buttom" for that task.   
5. You may need to login, with the Login User, the mail adress follows this pattern:
    F_ZXXXXXX.E_ZXXXXXX@trygdeetaten.no, where you change F_ZXXXXXX to F_Z992389 and E_ZXXXXXX to E_Z992389
    Use the same passord that you used to login in gosys.
    Username and password for testing can be found here(NAV-internal sites):
    https://confluence.adeo.no/display/KES/Generell+testing+av+sykemelding+2013+i+preprod
6. Consider whether the sykmelding should be rejected or approved
7. Trykk så på knappen "ferdigstill"
8. Then check that the task has been closed and completed in gosys


### Verification in «ditt sykefravær»:
1. Check that the sykmelding is on ditt sykefravær
2. Go to https://tjenester-q1.nav.no/sykefravaer
3. Log in with the fnr for the user as the username and a password
3. Then select "Uten IDPorten"
4. Enter the user's fnr again and press sign-in
5. Verify that a new task has appeared for the user

### Verification in Modia:
1. Log in to the modes, https://syfomodiaperson.nais.preprod.local/sykefravaer/$fnr
2. You may need to login, with the Login User, the mail adress follows this pattern:
    F_ZXXXXXX.E_ZXXXXXX@trygdeetaten.no, where you change F_ZXXXXXX to F_Z992389 and E_ZXXXXXX to E_Z992389
    Use the same passord that you used to login in gosys.
    Username and password for testing can be found here(NAV-internal sites):
    https://confluence.adeo.no/display/KES/Generell+testing+av+sykemelding+2013+i+preprod under "Verifisering i Modia"
3. See "Sykmeldt enkeltperson" verifying that the sykmelding that is there is correct

## Running in development mode
To run syfosmmanuell-backend locally you need a bunch of other services like Vault, a PostgreSQL database, an authentication service, Kafka, Zookeeper etc. 
The dependencies are available as a Docker compose setup at https://github.com/navikt/syfosmmanuell-backend-docker-compose 

To get started:
1. Check out the [syfosmmanuell-backend-docker-compose](https://github.com/navikt/syfosmmanuell-backend-docker-compose) repository and start the services as described in the [readme](https://github.com/navikt/syfosmmanuell-backend-docker-compose/blob/master/README.md) file.
2. Create a local run config for syfosmmanuell-backend pointing to Bootstrap.tk
3. Add the contents of dev-stack/dev-runtime-env as runtime environments in the run config. 

### For NAV employees
We are available at the Slack channel #team-sykmelding
