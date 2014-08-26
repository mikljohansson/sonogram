package se.embargo.sonar.io;

abstract class SonarWorker implements Runnable {
	private Thread _thread;
	protected volatile boolean _stop = true;
	
	public void start() {
		_stop = false;
		_thread = new Thread(this);
		_thread.start();
	}
	
	public void stop() {
		_stop = true;
		
		for (long ts = System.currentTimeMillis(); System.currentTimeMillis() < ts + 500 && _thread != null && _thread.isAlive(); ) {
			_thread.interrupt();
			
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException e) {
				break;
			}
		}
		
		_thread = null;
	}
}
