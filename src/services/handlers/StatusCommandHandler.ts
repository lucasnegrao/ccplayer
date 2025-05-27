import { ICommandHandler, Message, CommandResult } from '../../types/messaging';

export class StatusCommandHandler implements ICommandHandler {
    public readonly command = 'status';
    public readonly description = 'Get system status information';

    async handle(message: Message): Promise<CommandResult> {
        try {
            const status = {
                uptime: process.uptime(),
                memory: process.memoryUsage(),
                timestamp: new Date(),
                source: message.source,
                messageType: message.type
            };
            
            console.log(`Status requested from ${message.source}`);
            
            return {
                success: true,
                data: status
            };
        } catch (error) {
            return {
                success: false,
                error: `Failed to get status: ${error.message}`
            };
        }
    }
}
