
autobench --single_host --host1 localhost --uri1 /run.bash --port 3031 --quiet --low_rate 20 --high_rate 200 --rate_step 20 --num_call 10 --num_conn 500 --timeout 5 --file results.tsv 
