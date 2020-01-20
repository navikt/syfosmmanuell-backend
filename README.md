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
read postgresql/prod-fss/creds/syfosmmanuell-backend-admin

```

## Testing the whole flow for handling manual sykmelding in preprod
### Submitting sykmelding:
1. Navigate to syfomock: https://syfomock.nais.preprod.local/syfomock/opprettNyttMottak.html (Can only be accessed from developer image)
2. Note the number below Msgid (this changes with each submission)
3. Fill in the content you want in the sykmelding
4. Submit the sick leave

### Verification in the sykmelding applications:
1. Log in at https://logs.adeo.no and use the following search string: x_msgId: $ yourMsgId, ex: x_msgId: 58e1d88d-36fa-4756-a06a-32c384ba885f
2. Verify that what you expect to happen with a sykmelding actually happens. It should then be Ok | Manual processing | rejected
   What you look for are items: status = OK, status = MANUAL_PROCESSING or status = INVALID
3. Your message should het the status = MANUAL_PROCESSING  

### Verification in Gosys:
1. Login User (Case managers / supervisors):
   Z992392
2. Check that the sykmelding is placed in gosys:
   - Log in at https://gosys-nais-q1.nais.preprod.local/gosys
   - Search for user with fnr
3. Verify that there is a sykmelding task under tasks overview and 
   that this is the sykmelding you submitted
4. Click on the "Start buttom" for that task.   
5. You may need to login, with the Login User, the mail adress follows this pattern:
    F_ZXXXXXX.E_ZXXXXXX@trygdeetaten.no, where you change F_ZXXXXXX to F_Z992392 and E_ZXXXXXX to E_Z992392
    Use the same passord that you used to login in gosys.
    Username and password for testing can be found here(NAV-internal sites):
    https://confluence.adeo.no/display/KES/Generell+testing+av+sykemelding+2013+i+preprod
6. Consider whether the sykmelding should be rejected or approved
7. Trykk så på knappen "ferdigstill"
8. Then check that the task has been closed and completed in gosys


### Verification in «ditt sykefravær»:
1. Check that the sykmelding is on ditt sykefravær
2. Log in to user: https://service-q1.nav.no/sykefravaer/
3. Then select Without the ID port
4. Enter the user's name again and press sign-in
5. Verify that a new task has appeared for the user

### Verification in Modia:
1. Log in to the modes, https://app-q1.adeo.no/sykefravaer/
2. Log in with Case Manager: User: Z990625, password for testing can be found here (NAV internal sites):
   https://confluence.adeo.no/display/KES/Generell+testing+av+sykemelding+2013+i+preprod under "Verifisering i Modia"
3. See "Sykmeldt enkeltperson" verifying that the sykmelding that is there is correct

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`
* Jonas Henie, `jonas.henie@nav.no`

### For NAV employees
We are available at the Slack channel #team-sykmelding
