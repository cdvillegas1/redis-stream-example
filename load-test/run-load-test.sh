STACK_NAME=quarkus

API_URL=http://localhost:8090/v1/user/2

artillery run load-test.yml --target "$API_URL"