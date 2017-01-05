# ticktock

Distributed job scheduler, utilizing amqp.

This is a quick version of the service described in my [medium post](https://medium.com/@matt_rasband/distributed-scheduling-with-rabbit-505ab5e233ae#.ov35006y1). It's a simplistic scheduling system that guarantees single delivery for distributed jobs.

Note: ATM, this doesn't do leader election and isn't HA itself (yet).

## Build & Run

    ./mvnw clean package  # or just ./mvnw clean spring-boot:run
    java -jar target/ticktock.jar

### Example Job

Heroku dyno's go to sleep, unless called, after 30 minutes. You could easily have a worker based on this that auto-pings it:

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
            print('Pinging Heroku')
            # Do some check that you are within your hours...
            async with ClientSession() as session:
                async with session.get('https://my-heroku-app.com/health') as r:
                    print(r.status)
            print('Finished!')
    
        await channel.basic_consume(job, queue_name=work_q)
    
    loop = asyncio.get_event_loop()
    loop.create_task(main(loop))
    try:
        loop.run_forever()
    finally:
    loop.close()

Or the Java (Spring Boot) version, this is fairly ugly (no abstraction, yet):

    @Service
    public class MyWorker {
        @RabbitListener(bindings = 
            @QueueBinding(value = 
                @Queue(value = "${spring.application.name}.worker.my_job",
                    durable = "false",
                    // ensure that the message expires in some reasonable period,
                    // a safe number is 1/2 the time between events
                    arguments = {
                      @Argument(name = "x-message-ttl",
                                value = "150000",
                                type = "java.lang.Integer")
                    }),
                // The exchange name is defined by TickTock's properties
                exchange = @Exchange(value = "my.scheduler",
                    ignoreDeclarationExceptions = "true",
                    type = ExchangeTypes.TOPIC,
                    durable = "true"),
              key = "cron.minute.1")
        )
        public void myJob(@Header("timestamp") Date timestamp) {
            // Only one instance will receive the event and run it!
        }
    }