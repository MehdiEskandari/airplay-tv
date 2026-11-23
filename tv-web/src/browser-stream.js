// JMuxer's browser player uses Media Source. Its separate Node stream API is
// intentionally unavailable in the browser bundle; Node services use real streams.
export class Duplex {constructor(){throw new Error('Node streams are not available in the web player');}}
