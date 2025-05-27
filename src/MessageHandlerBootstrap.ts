import { MessageHandlingService } from './services/MessageHandlingService';
import { MqttMessageSource } from './services/sources/MqttMessageSource';
import { IntentMessageSource } from './services/sources/IntentMessageSource';
import { NotificationCommandHandler } from './services/handlers/NotificationCommandHandler';
import { StatusCommandHandler } from './services/handlers/StatusCommandHandler';

export class MessageHandlerBootstrap {
    private messageService: MessageHandlingService;
    private intentSource: IntentMessageSource;

    constructor() {
        this.messageService = new MessageHandlingService();
        this.intentSource = new IntentMessageSource();
        this.setupMessageHandling();
    }

    private setupMessageHandling(): void {
        // Register message sources
        this.messageService.registerMessageSource('mqtt', new MqttMessageSource(
            process.env.MQTT_BROKER_URL || 'mqtt://localhost:1883',
            {
                clientId: `yet-another-notifier-${Date.now()}`,
                clean: true,
                reconnectPeriod: 5000
            }
        ));

        this.messageService.registerMessageSource('intent', this.intentSource);

        // Register command handlers
        this.messageService.registerCommandHandler('notify', new NotificationCommandHandler());
        this.messageService.registerCommandHandler('status', new StatusCommandHandler());

        // Set up event listeners
        this.messageService.on('messageProcessed', ({ message, result }) => {
            console.log(`Message processed successfully:`, { 
                command: message.command, 
                source: message.source,
                success: result.success 
            });
        });

        this.messageService.on('messageError', ({ message, error }) => {
            console.error(`Message processing failed:`, { 
                command: message.command, 
                source: message.source, 
                error: error.message 
            });
        });

        this.messageService.on('unhandledCommand', (message) => {
            console.warn(`Unhandled command: ${message.command} from ${message.source}`);
        });
    }

    async start(): Promise<void> {
        await this.messageService.start();
        console.log('Message Handler Bootstrap completed');
        console.log('Registered sources:', this.messageService.getRegisteredSources());
        console.log('Registered commands:', this.messageService.getRegisteredCommands());
    }

    async stop(): Promise<void> {
        await this.messageService.stop();
    }

    getMessageService(): MessageHandlingService {
        return this.messageService;
    }

    getIntentSource(): IntentMessageSource {
        return this.intentSource;
    }
}
