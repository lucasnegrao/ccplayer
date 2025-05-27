import { EventEmitter } from 'events';
import { IMessageSource, ICommandHandler, Message, MessageType } from '../types/messaging';

export class MessageHandlingService extends EventEmitter {
    private messageSources: Map<string, IMessageSource> = new Map();
    private commandHandlers: Map<string, ICommandHandler> = new Map();
    private isRunning = false;

    async start(): Promise<void> {
        if (this.isRunning) return;
        
        console.log('Starting Message Handling Service...');
        
        // Start all message sources
        for (const [name, source] of this.messageSources) {
            try {
                await source.start();
                source.on('message', (message: Message) => this.handleMessage(message));
                console.log(`Message source '${name}' started`);
            } catch (error) {
                console.error(`Failed to start message source '${name}':`, error);
            }
        }
        
        this.isRunning = true;
        console.log('Message Handling Service started');
    }

    async stop(): Promise<void> {
        if (!this.isRunning) return;
        
        console.log('Stopping Message Handling Service...');
        
        // Stop all message sources
        for (const [name, source] of this.messageSources) {
            try {
                await source.stop();
                console.log(`Message source '${name}' stopped`);
            } catch (error) {
                console.error(`Failed to stop message source '${name}':`, error);
            }
        }
        
        this.isRunning = false;
        console.log('Message Handling Service stopped');
    }

    registerMessageSource(name: string, source: IMessageSource): void {
        this.messageSources.set(name, source);
        console.log(`Registered message source: ${name}`);
    }

    registerCommandHandler(command: string, handler: ICommandHandler): void {
        this.commandHandlers.set(command.toLowerCase(), handler);
        console.log(`Registered command handler: ${command}`);
    }

    private async handleMessage(message: Message): Promise<void> {
        try {
            console.log(`Processing message: ${message.command} from ${message.source}`);
            
            const handler = this.commandHandlers.get(message.command.toLowerCase());
            if (!handler) {
                console.warn(`No handler found for command: ${message.command}`);
                this.emit('unhandledCommand', message);
                return;
            }

            const result = await handler.handle(message);
            this.emit('messageProcessed', { message, result });
        } catch (error) {
            console.error(`Error processing message:`, error);
            this.emit('messageError', { message, error });
        }
    }

    getRegisteredSources(): string[] {
        return Array.from(this.messageSources.keys());
    }

    getRegisteredCommands(): string[] {
        return Array.from(this.commandHandlers.keys());
    }
}
