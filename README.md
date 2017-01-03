# ticktock

Distributed job scheduler, utilizing rabbit.

This is a quick version of the service described in my [medium post](https://medium.com/@matt_rasband/distributed-scheduling-with-rabbit-505ab5e233ae#.ov35006y1). It's an overly simplistic scheduling system pushing the majority of the work onto workers.

This can be implemented easily in your language of choice, though this should eventually have leader election to be HA itself if I can wrap my head around it :).

## Build & Run

    ./mvnw clean package  # or just ./mvnw clean spring-boot:run
    java -jar target/ticktock.jar

### Example Job

Heroku dyno's go to sleep, unless called. You could easily have a worker based on this that auto-pings it:


    #!/usr/bin/env python3.6
    import asyncio
    import aioamqp
    
    async def main(loop=None):
        if loop is None:
            loop = asyncio.get_event_loop()
    
        transport, protocol = await aioamqp.connect()
        channel = await protocol.channel()
        work_q = (await channel.queue_declare(queue_name='ping_my_heroku_app'))['queue']
        await channel.queue_bind(work_q, 'cron.schedule', routing_key='cron.minute.15')
    
        async def job(chan, body, envelope, properties):
            # Do some check that you are within your hours...
            async with ClientSession() as session:
                async with session.get('https://my-heroku-app.com/health') as r:
                    print(r.status)
            print('Performing job on minute...')
    
        await channel.basic_consume(job, queue_name=work_q)
    
    loop = asyncio.get_event_loop()
    loop.create_task(main(loop))
    try:
        loop.run_forever()
    finally:
    loop.close()
