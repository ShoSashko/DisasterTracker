# Overview
This program emulates disaster tracker integrated with nasa and google calendar API.

# Running the App

```bash
    # from the root run the following command
    sbt run
```

# Using the App

http://localhost:8080/api-docs - opens swagger 

Returns google events for requested dates
```bash
curl -X 'GET' \
  'http://localhost:8080/events/google?start=2021-12-22&end=2021-12-23' \
  -H 'accept: application/json'
```
Returns events and runs background job to track events from NASA EONET (https://eonet.gsfc.nasa.gov/docs/v3#api)
```bash
curl -X 'GET' \
  'http://localhost:8080/events/nasa' \
  -H 'accept: application/json'
```
Creates google event 

```bash
curl -X 'POST' \ 
  'http://localhost:8080/events' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "start": "2021-12-12T09:00:00-09:01",
  "end": "2021-12-12T09:05:00-09:10",
  "location": "Lviv, Ukraine",
  "description": "Event in Lviv",
  "bbox": "-129.02,50.73,-58.71,12.89",
  "eventId": ""
}'
```
