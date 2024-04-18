STACK_NAME=quarkus

API_URL=http://localhost/v1/user/2

artillery run load-test.yml --target "$API_URL"