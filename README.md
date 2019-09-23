![https://github.com/navikt/syfosmmanuell-backend/workflows/Deploy%20to%20dev%20and%20prod/badge.svg](Build status)
# SYFO mottak
This project contains the backend for handling manual sykmelding

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Jackson
* Postgres

## Getting started
## Running locally

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

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`

### For NAV employees
We are available at the Slack channel #team-sykmelding
