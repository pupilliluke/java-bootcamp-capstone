# Demo day runbook — student04 (Himank)

Adapted from Luke's runbook. Every student02 value swapped for student04.
Run each block in its own PowerShell terminal, from the repo root.

## ONLINE — Dr Kaur's server is up

1. Deploy (only if not already running):
   & 'C:\Program Files\Git\bin\bash.exe' k8s/cluster-deploy.sh

2. Point kubectl at your cluster:
   $env:KUBECONFIG = 'C:\Users\himan\.kube\student04.yaml'
   kubectl -n student04 get pods

3. Live logs (leave running — this is your correlation-id evidence):
   kubectl -n student04 logs -f deploy/crm-api

4. New terminal — frontend (leave running):
   npm --prefix frontend run dev

5. Open http://localhost:5173  ->  agent1 / agent1
   (NOT the nip.io URL directly: that is the API only and returns 401 at /)

6. New terminal — readiness through the ingress:
   curl.exe http://crm-student04.100.22.136.97.nip.io/actuator/health/readiness

7. Database check (no local psql on this machine, so use Docker):
   docker run --rm -it -e PGPASSWORD=$env:PGPASSWORD postgres:17 psql -h 100.22.136.97 -U student04 -d bootcamp
   # set first:  $env:PGPASSWORD = '<DB_Password from the sheet>'
   # then:  \dn   to list schemas,  select * from interaction;   to show rows

8. Kafka — watch your own topic:
   docker run --rm edenhill/kcat:1.7.1 -b 100.22.136.97:9092 -t student04.crm.interaction.v1 -C -o beginning

## OFFLINE — her server is down (fallback)

   docker compose up -d
   Rename-Item frontend\.env.local cluster.env.local.bak
   cd backend
   .\mvnw spring-boot:run            # leave running
   # new terminal:
   npm --prefix frontend run dev     # leave running
   # open http://localhost:5173 -> agent1 / agent1

   # only if the k8s beat is needed offline:
   k3d cluster start crm-local
   & 'C:\Program Files\Git\bin\bash.exe' k8s/smoke.sh

   # when her server returns:
   Rename-Item frontend\cluster.env.local.bak .env.local

## Your values (quick reference)

   Namespace / DB user / schema : student04
   Ingress host                 : crm-student04.100.22.136.97.nip.io
   Kafka topic                  : student04.crm.interaction.v1
   Kubeconfig                   : C:\Users\himan\.kube\student04.yaml
   Cluster host                 : 100.22.136.97
