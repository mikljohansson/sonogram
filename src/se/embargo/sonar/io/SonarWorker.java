package se.embargo.sonar.io;

abstract class SonarWorker implements Runnable {
	private final Thread _thread = new Thread(this);
	protected volatile boolean _stop = false;
	
	public void start() {
		_thread.start();
	}
	
	public void stop() {
		_stop = true;
		
		for (long ts = System.currentTimeMillis(); System.currentTimeMillis() < ts + 500 && _thread.isAlive(); ) {
			_thread.interrupt();
			
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException e) {
				break;
			}
		}
	}
}
